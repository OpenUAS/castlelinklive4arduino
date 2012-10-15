/*****************************************************************************
 *  CastleLinkLiveSerialMonitor
 *  Copyright (C) 2012  Matteo Piscitelli
 *  E-mail: matteo@picciux.it
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  For further info, check http://code.google.com/p/castlelinklive4arduino/
 *
 *  SVN $Id$
 ******************************************************************************
 *  SAFETY NOTICE
 *  Always keep in mind that an electric motor can be dangerous for you, for 
 *  people and for things. It can start at any time if there is power. 
 *  Castle Creations ESC are very good ones, and have many security
 *  strategies to avoid accidental and unwanted motor start.
 *  This program and CastleLinkLive library also try to keep things as safe as
 *  possible, but using them together with an Arduino (or similar) board 
 *  connected to an electric power system adds another possible point of 
 *  failure to your motor control chain.
 *  So please stay always on the safe side. If you have any doubts, ask
 *  other modelers to help.
 *  It's your responsibility to keep things safe. Developers of this software
 *  can't be considered liable for any possible damage will result from its use.
 ******************************************************************************
 *  CastleLinkLiveSerialMonitor is a program using CastleLinkLive 
 *  library to communicate over serial with a pc. It implements a serial 
 *  communication protocol to take commands from pc and send it back 
 *  ESC data. As we have plenty of power in a pc as compared to an arduino, 
 *  we send it raw data and give it responsibility to interpret and 
 *  transform in human-readable data.
 *  At http://code.google.com/p/castlelinklive4arduino/ you can find a 
 *  java library and programs implementing the other side of this protocol and 
 *  providing an UI to interact and show ESC data.
 ******************************************************************************/
 
#include "CastleLinkLiveSerialMonitor.h"

#include "config.h"
#include "CastleLinkLive.h"
#include "USART.h"
#include "protocol.h"

uint8_t state = STATUS_HELLO;

int loopDelay = 10;
int loopCnt = 0;

// control variables
boolean sendThrottle = true;
boolean autoGenThrottle = false;
boolean throttlePresent = false;

uint16_t tMin = 1000;
uint16_t tMax = 2000;
uint8_t nESC;

extern COMMAND *nextCmd;
extern COMMAND *lastCmd;
extern volatile uint8_t cmdQueueBusy;

void reply(uint8_t ack) {
  tx(OUT_RESPONSE_HEADER_H);
  tx(OUT_RESPONSE_HEADER_L | (ack & 0x01) ) ;
}

void processCommand(COMMAND *c) {
  int throttlePin = THROTTLE_IN_PIN;
  
  //process command
  switch(c->id) {
    case CMD_NOOP:
      break;

    case CMD_HELLO:
      if (state < STATUS_ARMED) {
        state = STATUS_CONF;
        reply(1);
      } else
        reply(0);
      break;
      
    case CMD_SET_TMIN:
      if (state == STATUS_CONF) {
        tMin = (c->h << 8) | c->l;
        reply(1);
      } else
        reply(0);
      break;
      
    case CMD_SET_TMAX:
      if (state == STATUS_CONF) {
        tMax = (c->h << 8) | c->l;
        reply(1);
      } else
        reply(0);    
      break;
      
    case CMD_SET_TMODE:
      if (state == STATUS_CONF) {
        autoGenThrottle = (c->l > 0);
        reply(1);
      } else
        reply(0);
    
      break;
      
    case CMD_SET_NESC:
      if (state == STATUS_CONF) {
        nESC = c->l;
        reply(1);
      } else
        reply(0);    
      break;

    case CMD_START:
      if (state == STATUS_CONF) {
        if (autoGenThrottle) 
          throttlePin = GENERATE_THROTTLE;
          
        if (CastleLinkLive.begin(nESC, throttlePin, tMin, tMax)) {
          state = STATUS_STARTED;
          reply(1);
        } else
          reply(0);
          
      } else
        reply(0);
      break;

      
    case CMD_ARM:
      if (state == STATUS_STARTED) {
        CastleLinkLive.throttleArm();
        state = STATUS_ARMED;
      } else
        reply(0);
      break;
      
    case CMD_SET_THROTTLE:
      if ( (state == STATUS_STARTED) || (state == STATUS_ARMED) ) {
        CastleLinkLive.setThrottle(c->l);
      } else
        reply(0);
      break;

    case CMD_DISARM:
      if (state == STATUS_ARMED) {
        CastleLinkLive.throttleDisarm();
        state = STATUS_STARTED;
      } else
        reply(0);
      break;
      
    default:
      reply(0);    
      break;
  }
  
}

void processCommandsQueue() {

  while(cmdQueueBusy) {}; //wait for command queue to be free;

  //extract command from queue
  COMMAND *c = nextCmd;

  while(c != NULL) { //if there are commands in queue 
    while(cmdQueueBusy) {}; //wait for command queue to be free;  
    nextCmd = c->next;
    if (nextCmd == NULL)
      lastCmd = NULL;
  
    processCommand(c);
  
    free(c);
    c = nextCmd;
  }
}

/*
 * event-handling funtion to attach to CastleLinkLive to be notified
 * when throttle signal failure (or recovery) is detected
 */
void throttlePresence(uint8_t present) {
  throttlePresent = present;
}

void setup() {
  //pinMode(13, OUTPUT);
  state = STATUS_HELLO;
  uart_init(SERIAL_BAUD_RATE);
  
  CastleLinkLive.init();
  
  //attaching our throttleFailure function to CastleLinkLive to be
  //notified in case of throttle failure/recovery
  CastleLinkLive.attachThrottlePresenceHandler(throttlePresence);

  uart_enable_interrupt();  
}

void sendData(uint8_t escID, CASTLE_RAW_DATA *data) {
  uint8_t buffer[OUT_DATA_BUFSIZE];
  uint8_t checksum = 0;
  uint8_t tp = 0;

  if (throttlePresent) tp = THROTTLE_PRESENT;

  buffer[0] = OUT_DATA_HEADER_H;
  buffer[1] = OUT_DATA_HEADER_L | tp | (escID & ESC_ID_MASK);
      
  checksum  = buffer[0];
  checksum ^= buffer[1];
      
  for (int i = 0; i < DATA_FRAME_CNT; i++) {
    buffer[(i * 2) + 2] = data->ticks[i] >> 8;
    buffer[(i * 2) + 3] = data->ticks[i] & 0xFF;
    checksum ^= buffer[(i * 2) + 2];
    checksum ^= buffer[(i * 2) + 3];
  }
      
  buffer[OUT_DATA_BUFSIZE -1] = checksum;
  txbuf(buffer, OUT_DATA_BUFSIZE);
   
}

void loop() {
  CASTLE_RAW_DATA escData;

  processCommandsQueue();  
  
  switch(state) {
    case STATUS_HELLO:
#if (LED_DISABLE == 0)
      if ( loopCnt == 0 )
        CastleLinkLive.setLed(0);
      else if (loopCnt == 10)
        CastleLinkLive.setLed(1);
#endif
      break;
    case STATUS_CONF:
#if (LED_DISABLE == 0)
      if ( (loopCnt == 0) || (loopCnt == 20) )
        CastleLinkLive.setLed(0);
      else if ( (loopCnt == 10) || (loopCnt == 30) )
        CastleLinkLive.setLed(1);
#endif
      break;
    case STATUS_STARTED:
#if (LED_DISABLE == 0)
      if ( (loopCnt == 0) || (loopCnt == 20) || (loopCnt == 40) )
        CastleLinkLive.setLed(0);
      else if ( (loopCnt == 10) || (loopCnt == 30) || (loopCnt == 50) )
        CastleLinkLive.setLed(1);
#endif
      break;
    case STATUS_ARMED:
      if (! throttlePresent) {
        memset(&escData, 0, sizeof(CASTLE_RAW_DATA));
        escData.ticks[FRAME_REFERENCE] = 2000;
        escData.ticks[FRAME_TEMP2] = 1000;
        sendData(0, &escData);
        delay(100);
      } else {
        for (int e = 0; e < nESC; e++) {
          if (CastleLinkLive.getData(e, &escData)) 
            sendData(e, &escData);  
          
          processCommandsQueue();  
        }
      }
      break; 
  }
  
  loopCnt++;
  loopCnt = loopCnt % 100;
  delay(loopDelay);
}
