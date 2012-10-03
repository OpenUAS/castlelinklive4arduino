/* SVN $Id$ */

/*****************************************************************************
 *  CastleLinkLive Library for Arduino - CastleLinkLive.h
 *  Copyright (C) 2012  Matteo Piscitelli (matteo@picciux.it)
 *  Rewritten low-level from a high-level work of Capaverde at rcgroups.com
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
 ******************************************************************************/
 
 /*****************************************************************************
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
 ******************************************************************************/

/*
  This library if useful to get live telemetry data from
  Castle Creations ESCs with CastleLinkLive protocol
  available and enabled.
*/

#ifndef CastleLinkLive_h
#define CastleLinkLive_h

#ifndef _STDDEF_H
#include "stddef.h"
#endif
#ifndef Pins_Arduino_h
#include "pins_arduino.h"
#endif

#include "CastleLinkLive_config.h"

/*
#if defined(__AVR_ATmega1280__) || defined(__AVR_ATmega2560__) || defined (__AVR_ATmega168__) || defined(__AVR_ATmega328__)
#define MAX_ESCS 2
#elseif defined (__AVR_ATmega8__)
#define MAX_ESCS 1
#else
#define MAX_ESCS 0
#endif
*/

#ifndef MAX_ESCS
#define MAX_ESCS 2
#endif

/*
 * Since castle pins are externally pulled-up as required by castle link live spec,
 * we need to keep disabled internal pullups when pins are used as inputs. We have two choices:
 * 1) globally disable all port pullups
 * 2) do a two-step process on every switch: 
        - from OUTPUT to INPUT: since pins are HIGH when switching them to input mode, immediately
                                write them LOW to disable the pullups
        - from INPUT to OUTPUT: switching the pin directly to output would cause an output LOW
                                towards the ESC(s), which we don't want; so write them HIGH before, temporarily
                                enabling pullups, then switch them to output, obtaining them to stay HIGH.
                                
 * You can choose option 1 by keeping following define un-commented, otherwise option 2 will be enforced
 */
#define DISABLE_ALL_PULLUPS

#define FRAME_RESET            -1
#define FRAME_REFERENCE        0
#define FRAME_VOLTAGE          1
#define FRAME_RIPPLE_VOLTAGE   2
#define FRAME_CURRENT          3
#define FRAME_THROTTLE         4
#define FRAME_OUTPUT_POWER     5
#define FRAME_RPM              6
#define FRAME_BEC_VOLTAGE      7
#define FRAME_BEC_CURRENT      8
#define FRAME_TEMP1            9
#define FRAME_TEMP2           10
#define DATA_FRAME_CNT        11 //not counting the reset frame

#define GENERATE_THROTTLE     -1

typedef struct castle_raw_data_struct {
   uint16_t ticks[DATA_FRAME_CNT];
} CASTLE_RAW_DATA;

typedef struct castle_esc_data_struct {
  float voltage;
  float rippleVoltage;
  float current;
  float throttle;
  float outputPower;
  float RPM;
  float BECvoltage;
  float BECcurrent;
  float temperature;
} CASTLE_ESC_DATA;

class CastleLinkLiveLib {
  public:
   CastleLinkLiveLib();
   void init();
   uint8_t begin();
   uint8_t begin(uint8_t nESC);
   uint8_t begin(uint8_t nESC, int throttlePinNumber);
   uint8_t begin(uint8_t nESC, int throttlePinNumber, uint16_t throttleMin, uint16_t throttleMax);
   void setThrottle(uint8_t throttle);
   uint8_t getData(uint8_t index, CASTLE_ESC_DATA *dataHolder);
   uint8_t getData(uint8_t index, CASTLE_RAW_DATA *dataHolder);
   void attachThrottlePresenceHandler(void (*ptHandler) (uint8_t) );
   void throttleArm();
   void throttleDisarm();

#ifndef LED_DISABLE
   void setLed(uint8_t on);
#endif

  private:
   int _throttlePinNumber;
   uint8_t _nESC;

   volatile uint8_t *_pcicr;
   uint8_t _pcie;
   volatile uint8_t *_pcmsk;
   uint8_t _pcint;
   volatile uint8_t *_throttlePortModeReg;
   
   volatile uint8_t _throttle;

   void _init_data_structure(int i);
   void _timer_init();
   uint8_t _setThrottlePinRegisters();
   uint8_t _copyDataStructure(uint8_t index, CASTLE_RAW_DATA *dest );
};

extern CastleLinkLiveLib CastleLinkLive;

#endif //def CastleLinkLive_h
