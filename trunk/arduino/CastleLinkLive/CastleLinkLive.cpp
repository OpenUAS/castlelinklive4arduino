/*****************************************************************************
 *  CastleLinkLive Library for Arduino - CastleLinkLive.cpp
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
 *  SVN $Id$
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

#ifndef WProgram_h
#include "WProgram.h"
#endif

#include "CastleLinkLive.h"

/***************************************
 * TIMER1 macros
 ***************************************/
#define TIMER1_RESOLUTION 0xFFFFu

#ifdef DEBUG_SLOW_MOTION

#define TIMER1_PRESCALER 1024 //overflows every 4.19s - tick 6.4us @ 16MHz
#define TIMER_START() (TCCR1B |=  _BV(CS12) | _BV(CS10)) //prescaler 1024

#else

#define TIMER1_PRESCALER 8 //overflows every 32.767ms - tick 0.5us @ 16MHz
#define TIMER_START() (TCCR1B |=  _BV(CS11)) //prescaler 8

#endif

#define TIMER1_FREQ (F_CPU / TIMER1_PRESCALER)
#define TIME2TICKS(T) ( T * TIMER1_FREQ )

// generate throttle
#ifndef THROTTLEGEN_PERIOD
#define THROTTLEGEN_PERIOD 0.02f //20ms throttle period
#endif

#ifndef THROTTLE_MIN
#define THROTTLE_MIN 0.001f   // 1ms min
#endif

#ifndef THROTTLE_MAX
#define THROTTLE_MAX 0.002f   // 2ms max
#endif

#define TG_INTERVAL ( TG_MAX - TG_MIN )

#define TG_PERIOD_TICKS (THROTTLEGEN_PERIOD * TIMER1_FREQ) //20ms period
//#define TG_MIN_TICKS ( TG_MIN * TIMER1_FREQ )
//#define TG_MAX_TICKS ( TG_MAX * TIMER1_FREQ )
//#define TG_INTERVAL_TICKS ( TG_INTERVAL * TIMER1_FREQ )

#define TIMER_CLEAR() ( TCNT1 = 0 )
#define TIMER_STOP() ( TCCR1B = 0 )
#define TIMER_IS_RUNNING() ( (TCCR1B & _BV(CS10)) || (TCCR1B & _BV(CS11)) || (TCCR1B & _BV(CS10)) )

#define CASTLE_RESET_TIMEOUT 0.006f //6 ms: castle tick has to come before
#define TIMER_RESET_TICKS (CASTLE_RESET_TIMEOUT * TIMER1_FREQ)

#define THROTTLE_SIGNAL_TIMEOUT 1.0f //1 sec timeout from RX
#define MAX_OVERFLOW ( THROTTLE_SIGNAL_TIMEOUT / ( ((float) TIMER1_RESOLUTION) / ((float) TIMER1_FREQ)) )
#define MAX_NO_THROTTLE_GEN ( THROTTLE_SIGNAL_TIMEOUT / THROTTLEGEN_PERIOD )
#define WAITFORDATA_TIMEOUT ( THROTTLE_SIGNAL_TIMEOUT * 500) //throttle signal timeout / 2 * 1000

#define THROTTLE_PRESENCE_FLAG   0x80

#define SET_THROTTLE_PRESENT() ( flags |= THROTTLE_PRESENCE_FLAG )
#define SET_THROTTLE_NOT_PRESENT() ( flags &= ~ THROTTLE_PRESENCE_FLAG )
#define IS_THROTTLE_PRESENT() ( flags & THROTTLE_PRESENCE_FLAG )

#define LED_INIT() ( DDRB |= _BV(DDB5) )

#define LED_ON() ( PORTB |= _BV(PORTB5) )
#define LED_OFF() ( PORTB &= ~ _BV(PORTB5) )

#define LED_FLIP() ( PINB |= _BV(PINB5) )

#define LED_STATUS() ( PORTB & _BV(PORTB5) )

#define LED_MIN_MOD 100
#define LED_MAX_MOD 1

//definitions from pins_arduino.c
#define PA 1
#define PB 2
#define PC 3
#define PD 4
#define PE 5
#define PF 6
#define PG 7
#define PH 8
#define PJ 10
#define PK 11
#define PL 12

/** \cond skipthis */
typedef struct castle_priv_data_struct {
   uint16_t ticks[DATA_FRAME_CNT];

   int frameIdx;
   uint8_t ticked;
   uint8_t ready;
} CASTLE_PRIV_DATA;
/** \endcond */

CASTLE_PRIV_DATA data[MAX_ESCS];

volatile uint8_t flags = 0; 

uint8_t throttleFailCnt = 0;

#ifndef LED_DISABLE
uint8_t ledCnt = 0;
volatile uint8_t ledMod = LED_MAX_MOD;
#endif

uint8_t throttlePinMask;
uint16_t throttlePulseHighTicks;
uint16_t throttlePulseLowTicks;

uint8_t esc1PinMask = _BV(PIND2);
uint8_t escPinsHighMask = _BV(PORTD2) | _BV(PORTD3);
uint8_t escPinsLowMask = ~ escPinsHighMask;

uint8_t extIntClearMask = _BV(INTF0) | _BV(INTF1);
uint8_t extIntEnableMask = _BV(INT0) | _BV(INT1);
uint8_t extIntDisableMask = ~ extIntEnableMask;

uint16_t _throttleMinTicks;
uint16_t _throttleMaxTicks;
uint16_t _throttleIntervalTicks;

void (*throttlePresenceHandler) (uint8_t) = NULL;

/* 
 * CastleLinkLiveLib class
 ****************************************************/
 
/*
 * Constructor
 */
CastleLinkLiveLib::CastleLinkLiveLib() {
  init();
}

void CastleLinkLiveLib::init() {
  _timer_init();
  _throttlePinNumber = GENERATE_THROTTLE;
  _nESC = 0;
  
  LED_INIT();
  LED_OFF();    
}

/*
 * PRIVATE FUNCTIONS
 ****************************************************/
void CastleLinkLiveLib::_init_data_structure(int i) {
  data[i].frameIdx = FRAME_RESET;
  data[i].ready = 0;
  data[i].ticked = 0;
  for (int j = 0; j < DATA_FRAME_CNT; j++)
    data[i].ticks[j] = 0;
}

/*
 * Inits 16bit TIMER1
 */
void CastleLinkLiveLib::_timer_init() {
  // stop timer and set normal mode
  TCCR1B = 0; 
  TCCR1A = 0;
  TCNT1 = 0; //timer reset
}


uint8_t CastleLinkLiveLib::_setThrottlePinRegisters() {
  _pcicr = &PCICR;
  
  uint8_t port = digitalPinToPort(_throttlePinNumber);
  
  if (port == NOT_A_PORT) return false;
  
  _throttlePortModeReg = portModeRegister(port);
  throttlePinMask = digitalPinToBitMask(_throttlePinNumber);
  
  switch(port) {
    case PB:
       _pcmsk = &PCMSK0;
       _pcie = PCIE0;
       break;
      
    case PC:
       _pcmsk = &PCMSK1;
       _pcie = PCIE1;
       break;
      
    case PD:
       _pcmsk = &PCMSK2;
       _pcie = PCIE2;
       break;

    default:
      return false;    
  }

  if (_throttlePinNumber >= 0 && _throttlePinNumber <= 7) { //0-7 => PORTD: PCINT16-23
    _pcint = PCINT16 + _throttlePinNumber;
  } else if (_throttlePinNumber >= 8 && _throttlePinNumber <= 13) { //8-13 => PORTB: PCINT0-5 {
    _pcint = PCINT0 + (_throttlePinNumber - 8);
  } else if (_throttlePinNumber >= A0 && _throttlePinNumber <= A5) { //A0-A5 => PORTC: PCINT8-13
    _pcint = PCINT8 + (_throttlePinNumber - A0);
  } else
    return false;
  
  return true;
}

uint8_t CastleLinkLiveLib::_copyDataStructure(uint8_t index, CASTLE_RAW_DATA *dest ) {
  uint8_t mask = _BV(index);  
  flags |= mask; //set busy flag for desired ESC
  
  unsigned long startWait = millis();
  while (flags & mask) { //wait for ISRs code to finish filling data structure
    if (millis() - startWait >= WAITFORDATA_TIMEOUT) return false;
  }
  memcpy(dest, &(data[index]), sizeof(uint16_t) * DATA_FRAME_CNT); 
  
  return true;
}

/* 
 * PUBLIC FUNCTIONS
 *****************************************************/
#ifndef LED_DISABLE
void CastleLinkLiveLib::setLed(uint8_t on) {
  if ( TIMER_IS_RUNNING() ) return;
  
  if (on)
   LED_ON();
  else
   LED_OFF(); 
}
#endif
 
uint8_t CastleLinkLiveLib::begin(uint8_t nESC, int throttlePinNumber, uint16_t throttleMin, uint16_t throttleMax) {
  if ( (nESC > MAX_ESCS) || (nESC <= 0) ) return false;
  _nESC = nESC;
  
  _throttlePinNumber = throttlePinNumber;
  
  if (_throttlePinNumber > GENERATE_THROTTLE) {
    if (! _setThrottlePinRegisters()) return false;
  }
  
  cli();

#ifdef DISABLE_ALL_PULLUPS  
  MCUCR |= _BV(PUD); // disable all-port pull-ups
#endif  
  
  _timer_init();

  if (nESC == 2) {
    EICRA = _BV(ISC01) | _BV(ISC11); // configure falling edge detection on INT0 and INT1
  } else {
    EICRA = _BV(ISC01); //configure falling edge detection on INT0
   
    escPinsHighMask = _BV(PORTD2);
    escPinsLowMask = ~ escPinsHighMask;

    extIntClearMask = _BV(INTF0);
    extIntEnableMask = _BV(INT0);
    extIntDisableMask = ~ extIntEnableMask;
  }

  DDRD |= escPinsHighMask; //set ESCs pins as outputs
  PORTD |= escPinsHighMask; //set ESCs pins high

  // set output compare match A of timer1 with number of ticks
  // corresponding to CASTLE_RESET_TIMEOUT
  OCR1A = TIMER_RESET_TICKS; 

  // enable output compare match A interrupt generation
  TIMSK1 |= _BV(OCIE1A);

  //init data structures
  for (int i = 0; i < _nESC; i++) _init_data_structure(i);

  if (_throttlePinNumber == GENERATE_THROTTLE) { // if auto-generating throttle...
    // set output compare match B with number of ticks
    // corresponding to TG_PERIOD
    OCR1B = TG_PERIOD_TICKS;

    TIMSK1 |= _BV(OCIE1B); //enable output compare match B interrupt generation
    
  } else {
    *_throttlePortModeReg &= ~ ( throttlePinMask ); //set throttle pin as input
    TIMSK1 |= _BV(TOIE1); //enable timer overflow interrupt    
  }

  /* 
   * SAFETY
   * rest of throttle receiving/generating initialization will not continue alone: 
   * program must explicitly call "throttleArm" function to continue.
   */

  _throttleMinTicks = throttleMin * (TIMER1_FREQ / 1000000);
  _throttleMaxTicks = throttleMax * (TIMER1_FREQ / 1000000);
  _throttleIntervalTicks = (throttleMax - throttleMin) * (TIMER1_FREQ / 1000000);
  

  sei(); //ready to go: enable interrupts
  return true;
  
}

uint8_t CastleLinkLiveLib::begin(uint8_t nESC, int throttlePinNumber) {
  return begin(nESC, throttlePinNumber, THROTTLE_MIN * 1000000.0f, THROTTLE_MAX * 1000000.0f);  
}
uint8_t CastleLinkLiveLib::begin(uint8_t nESC) {
  return begin(nESC, GENERATE_THROTTLE, THROTTLE_MIN * 1000000.0f, THROTTLE_MAX * 1000000.0f);
}

uint8_t CastleLinkLiveLib::begin() {
  return begin(1, GENERATE_THROTTLE, THROTTLE_MIN * 1000000.0f, THROTTLE_MAX * 1000000.0f);
}

void CastleLinkLiveLib::throttleArm() {
  cli();
  
  if (_throttlePinNumber == GENERATE_THROTTLE) {
    SET_THROTTLE_PRESENT();
    setThrottle(50);
    SET_THROTTLE_NOT_PRESENT();    
  } else {
    *_pcmsk = _BV(_pcint); //enable throttle pin to generate port-interrupt
    *_pcicr = _BV(_pcie); //enable throttle pin port to generate interrupt    
  }
  
  SET_THROTTLE_NOT_PRESENT();
  
  sei();
  TIMER_START();
}

void CastleLinkLiveLib::throttleDisarm() {
  cli();
  TIMER_STOP();

  if (_throttlePinNumber != GENERATE_THROTTLE) {
    *_pcmsk &= ~ _BV(_pcint); //disable throttle pin port-interrupt generation
    *_pcicr &= ~ _BV(_pcie); //disable throttle pin port interrupt generation
  }
  
  LED_OFF();
  
  sei(); 
}

void CastleLinkLiveLib::attachThrottlePresenceHandler(void (*ptHandler) (uint8_t) ) {
  throttlePresenceHandler = ptHandler; 
}

void CastleLinkLiveLib::setThrottle(uint8_t throttle) {
  if (_throttlePinNumber > GENERATE_THROTTLE) return;
  
  throttleFailCnt = 0; //reset throttle failure counter
  
  if (throttle < 0)
    _throttle = 0;
  else if (throttle > 100)
    _throttle = 100;
  else
    _throttle = throttle;
  
  uint16_t tpht = _throttleMinTicks + ( _throttleIntervalTicks / 100.0f * ((float) _throttle) );
  uint16_t tplt = TG_PERIOD_TICKS - tpht;
  
  cli(); 
  throttlePulseHighTicks = tpht;
  throttlePulseLowTicks = tplt;
  sei();
  
  if (! (TIMSK1 & _BV(OCIE1B)) ) {
    TIMER_CLEAR();
    OCR1B = TG_PERIOD_TICKS;
    TIMSK1 |= _BV(OCIE1B);
  }
  
  if (! IS_THROTTLE_PRESENT() ) {
    SET_THROTTLE_PRESENT();
    if (throttlePresenceHandler) 
      throttlePresenceHandler(1); 
  }
  
#ifndef LED_DISABLE  
  ledMod = 100 - _throttle + 1;
#endif
}

//! [ESC data calculation details]
uint8_t CastleLinkLiveLib::getData( uint8_t index, CASTLE_ESC_DATA *o) {
  uint16_t refTicks;
  uint16_t offTicks;
  uint16_t ticks;
  uint8_t whichTemp;
  float value;

  CASTLE_RAW_DATA c;
  
  c.ticks[FRAME_REFERENCE] = 0;
  
  if (! _copyDataStructure(index, &c)) return false; //data was not ready
  
  refTicks = c.ticks[FRAME_REFERENCE];
  if (c.ticks[FRAME_TEMP1] < c.ticks[FRAME_TEMP2]) {
    offTicks = c.ticks[FRAME_TEMP1];
    whichTemp = FRAME_TEMP2;
  } else {
    offTicks = c.ticks[FRAME_TEMP2];    
    whichTemp = FRAME_TEMP1;
  }

  if (refTicks == 0) return false; //data was not ready
  
  for (int f = 1; f < DATA_FRAME_CNT; f++) {
    ticks = c.ticks[f] - offTicks;
    
    value = ((float) ticks) / ((float) refTicks);
    
    switch(f) {
      case FRAME_VOLTAGE:
        o->voltage = value * 20.0f;
        break;
      case FRAME_RIPPLE_VOLTAGE:
        o->rippleVoltage = value * 4.0f;
        break;
      case FRAME_CURRENT:
        o->current = value * 50.0f;
        break;
      case FRAME_THROTTLE:
        o->throttle = value;
        break;
      case FRAME_OUTPUT_POWER:
        o->outputPower = value * 0.2502f;
        break;
      case FRAME_RPM:
        o->RPM = value * 20416.7f;
        break;
      case FRAME_BEC_VOLTAGE:
        o->BECvoltage = value * 4.0f;
        break;
      case FRAME_BEC_CURRENT:
        o->BECcurrent = value * 4.0f;
        break;
      case FRAME_TEMP1:
        if (whichTemp == FRAME_TEMP1) o->temperature = value * 30.0f;
        break;
      case FRAME_TEMP2:
        if (whichTemp == FRAME_TEMP2) {
           if (value > 3.9f)
             o->temperature = -40;
           else {
             float v = value * 63.8125f;
             o->temperature = 1.0f / (log(v * 10200.0f / (255 - v) / 10000.0f) / 3455.0f + 1.0f / 298.0f) - 273;
           }
        }
        break;
    }
    
  }
  
  return true;
}
//! [ESC data calculation details]


uint8_t CastleLinkLiveLib::getData( uint8_t index, CASTLE_RAW_DATA *o) {
  return _copyDataStructure(index, o); 
}

/*
 * Interrupt Service Routines and related functions
 ****************************************************/

//=== INT0/INT1 (external interrupts) handlers: get data from ESC(s)
inline void escInterruptHandler(uint8_t index) {
  uint16_t ticks = TCNT1;

  if (ticks == 0) return; //timer was stopped
  
  CASTLE_PRIV_DATA *d = &(data[index]);
  
  d->frameIdx++;
  
  d->ticks[d->frameIdx] = ticks;
  d->ticked = true;
  
  d->ready = (d->frameIdx == DATA_FRAME_CNT -1);
}

ISR(INT0_vect) {
  escInterruptHandler(0);
}

ISR(INT1_vect) {
  escInterruptHandler(1);
}

//=== PinChange interrupt handlers: get throttle signal
inline void throttleInterruptHandler(uint8_t pinStatus) {
  if ( pinStatus ) {  // throttle pulse start
     PORTD &= escPinsLowMask; //write LOW to ESCs pins
     TIMER_CLEAR();
#ifndef LED_DISABLE
     ledCnt++;
     ledCnt = ledCnt % ledMod;
     if (ledCnt == 0)
       LED_ON();
     else
       LED_OFF();
#endif
  } else {                            // throttle pulse end
     PORTD |= escPinsHighMask; //write high to ESCs pins
#ifndef LED_DISABLE
     uint16_t t = TCNT1;
#endif
     TIMER_CLEAR();

#ifndef LED_DISABLE
     if (t < _throttleMinTicks)
       t = 0;
     else
       t -= _throttleMinTicks;
     
     if (t >= _throttleIntervalTicks)
       ledMod = 1;
     else
       ledMod = 100 - ( t / ((float) (_throttleIntervalTicks)) * 100.0f) + 1;
#endif

     DDRD &= escPinsLowMask; //set castle pins as inputs
#ifndef DISABLE_ALL_PULLUPS  
     PORTD &= escPinsLowMask; //write LOW to castle pins to disable pullups if not globally disabled
#endif
     EIFR |= extIntClearMask; // clear INT0 INT1 flags before enabling interrupts
     EIMSK |= extIntEnableMask; //enable interrupts on INT0 and INT1
  }
  
  throttleFailCnt = 0; //reset throttle failure counter  
  if (! IS_THROTTLE_PRESENT() ) {
    SET_THROTTLE_PRESENT();
    if (throttlePresenceHandler)
      throttlePresenceHandler(1);
  }
}

inline void throttleNotPresent() {
  if ( IS_THROTTLE_PRESENT() ) {
    SET_THROTTLE_NOT_PRESENT();
    LED_OFF();
    if (throttlePresenceHandler) {
      throttlePresenceHandler(0);
    }
  }  
}

#ifdef PCIE0
// PORTB
ISR(PCINT0_vect) {
  throttleInterruptHandler( PINB & throttlePinMask );
}
#endif

#ifdef PCIE1
//PORTC
ISR(PCINT1_vect) {
  throttleInterruptHandler( PINC & throttlePinMask );
}
#endif

#ifdef PCIE2
//PORTD
ISR(PCINT2_vect) {
  throttleInterruptHandler( PIND & throttlePinMask );
}
#endif

//=== TIMER1 interrupts handlers

// castle data timeout
ISR(TIMER1_COMPA_vect) {  
  EIMSK &= extIntDisableMask; //disable INT0/INT1 interrupt
  // timeout elapsed, so restore output mode for ESC pins in any case
#ifndef DISABLE_ALL_PULLUPS  
  PORTD |= escPinsHighMask; //write high to castle pind before switching to output if pullups are not globally disabled
#endif
  DDRD |= escPinsHighMask;  //set castle pind to output  

  for (int i = 0; i < MAX_ESCS; i++) {     
    //if castle ESC ticked some data in, reset the ticked indicator for next cycle
    //otherwise, it was a reset frame
    if (data[i].ticked)
      data[i].ticked = false;
    else 
      data[i].frameIdx = FRAME_RESET;

    if (data[i].frameIdx == FRAME_RESET && data[i].ready) {
      flags &= ~ _BV(i); //data for this ESC is ready: clear busy flag
      data[i].ready = false;
    }
  }

#ifndef LED_DISABLE  
  if (! IS_THROTTLE_PRESENT() ) {
    ledCnt++;
    ledCnt = ledCnt % 100;
    if ( (ledCnt == 0) || (ledCnt == 6) || (ledCnt == 12) ) 
      LED_ON();
    else if ( (ledCnt == 3) || (ledCnt == 9) || (ledCnt == 15) )
      LED_OFF();
  }
#endif
  
}

// generated throttle interrupts
ISR(TIMER1_COMPB_vect) {
  PIND |= escPinsHighMask; //toggle esc pins
  TIMER_CLEAR(); //clear timer
  
  if ( ! (PORTD & _BV(PORTD2)) ) {
    OCR1B = throttlePulseHighTicks; //set COMPB to generate throttle pulse throttleTicks-long

#ifndef LED_DISABLE
    ledCnt++;
    ledCnt = ledCnt % ledMod;
    if (ledCnt == 0)
      LED_ON();
    else
      LED_OFF();
#endif
  } else {
    OCR1B = throttlePulseLowTicks;
 
    DDRD &= escPinsLowMask; //set castle pins as inputs
#ifndef DISABLE_ALL_PULLUPS  
    PORTD &= escPinsLowMask; //write LOW to castle pins to disable pullups if not globally disabled
#endif
    EIFR |= extIntClearMask; // clear INT0 INT1 flags before enabling
    EIMSK |= extIntEnableMask; //enable interrupts on INT0 and INT1

    throttleFailCnt++; //increase throttle failure counter: 
  }
  
  
  if (throttleFailCnt >= MAX_NO_THROTTLE_GEN) {
    TIMSK1 &= ~ _BV(OCIE1B); //disable interrupt generation
    PORTD |= escPinsHighMask; //pins high!
    DDRD |= escPinsHighMask; //pins as output!
    throttleNotPresent();
  }
}

// overflow: won't fire if regular throttle signal (external) is present
ISR(TIMER1_OVF_vect) {
  throttleFailCnt++; //increase throttle failure counter

  if (throttleFailCnt >= MAX_OVERFLOW) {
    PORTD |= escPinsHighMask;
    DDRD |= escPinsHighMask;
    throttleNotPresent();
    throttleFailCnt = 0; //reset throttle failure counter
  }
}

/*
 * Pre-istantiate object
 ****************************************************/
CastleLinkLiveLib CastleLinkLive;


