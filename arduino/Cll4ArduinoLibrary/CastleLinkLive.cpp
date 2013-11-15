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

#include "pins_arduino.h"
#if defined(ARDUINO) && ARDUINO >= 100
#include "Arduino.h"
#else
#include "WProgram.h"
#endif

#include "CastleLinkLive_config.h"
#include "CastleLinkLive.h"

#if defined (__AVR_ATmega168__) || defined(__AVR_ATmega328P__)
#include "atmega168_328.h"
#elif defined(__AVR_ATmega32U4__)
#include "atmega32u4.h"
#elif defined(__AVR_ATmega1280__) || defined(__AVR_ATmega2560__)
#include "atmega1280_2560.h"
#elif defined (__AVR_ATmega8__)
#error "Old Arduinos ATmega8-based are not supported ATM"
#else
#define MAX_ESCS 0
#error "MCU not supported"
#endif


/***************************************
 * TIMER macros
 ***************************************/
#define TIMER_RESOLUTION 0xFFFFu


//ATmega 168 / 328 settings
/*#if defined (__AVR_ATmega168__) || defined(__AVR_ATmega328P__)
#include "atmega168_328.h"
//ATmega32U4 settings
#elif defined (__AVR_ATmega32U4__)
#include "atmega32U4.h"
#endif
*/

#define TIMER_FREQ (F_CPU / TIMER_PRESCALER)
#define TIME2TICKS(T) ( T * TIMER_FREQ )

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

#define TG_PERIOD_TICKS (THROTTLEGEN_PERIOD * TIMER_FREQ) //20ms period
//#define TG_MIN_TICKS ( TG_MIN * TIMER1_FREQ )
//#define TG_MAX_TICKS ( TG_MAX * TIMER1_FREQ )
//#define TG_INTERVAL_TICKS ( TG_INTERVAL * TIMER1_FREQ )


#define CASTLE_RESET_TIMEOUT 0.006f //6 ms: castle tick has to come before
#define TIMER_RESET_TICKS (CASTLE_RESET_TIMEOUT * TIMER_FREQ)

#define THROTTLE_SIGNAL_TIMEOUT 1.0f //1 sec timeout from RX
#define MAX_OVERFLOW ( THROTTLE_SIGNAL_TIMEOUT / ( ((float) TIMER_RESOLUTION) / ((float) TIMER_FREQ)) )
#define MAX_NO_THROTTLE_GEN ( THROTTLE_SIGNAL_TIMEOUT / THROTTLEGEN_PERIOD )
#define WAITFORDATA_TIMEOUT ( THROTTLE_SIGNAL_TIMEOUT * 500) //throttle signal timeout / 2 * 1000

#define THROTTLE_PRESENCE_FLAG   0x80

#define SET_THROTTLE_PRESENT() ( flags |= THROTTLE_PRESENCE_FLAG )
#define SET_THROTTLE_NOT_PRESENT() ( flags &= ~ THROTTLE_PRESENCE_FLAG )
#define IS_THROTTLE_PRESENT() ( flags & THROTTLE_PRESENCE_FLAG )

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

#if (LED_DISABLE == 0)
uint8_t ledCnt = 0;
volatile uint8_t ledMod = LED_MAX_MOD;
#endif

uint8_t throttlePinMask;
uint16_t throttlePulseHighTicks;
uint16_t throttlePulseLowTicks;

uint8_t esc1PinMask; // = _BV(PIND2);
uint8_t escPinsHighMask; // = _BV(PORTD2) | _BV(PORTD3);
uint8_t escPinsLowMask; // = ~ escPinsHighMask;

uint8_t extIntClearMask; // = _BV(INTF0) | _BV(INTF1);
uint8_t extIntEnableMask; // = _BV(INT0) | _BV(INT1);
uint8_t extIntDisableMask; // = ~ extIntEnableMask;

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
  TIMER_STOP();
  TIMER_INIT();
  TIMER_CLEAR();
}


uint8_t CastleLinkLiveLib::_setThrottlePinRegisters() {
  _pcicr = &PCICR;
  
  uint8_t port = digitalPinToPort(_throttlePinNumber);
  
  if (port == NOT_A_PORT) return false;
  
#if defined (__AVR_ATmega32U4__)
  if (port != PB) return false; //on ATmega32U4 we have PCINT only on PORTB
#endif

  _throttlePortModeReg = portModeRegister(port);
  throttlePinMask = digitalPinToBitMask(_throttlePinNumber);
  
  switch(port) {
    case PB:
       _pcmsk = &PCMSK0;
       _pcie = PCIE0;
       break;

#if defined (__AVR_ATmega168__) || defined(__AVR_ATmega328P__)
    case PC:
       _pcmsk = &PCMSK1;
       _pcie = PCIE1;
       break;
      
    case PD:
       _pcmsk = &PCMSK2;
       _pcie = PCIE2;
       break;
#endif

    default:
      return false;    
  }

#if defined (__AVR_ATmega168__) || defined(__AVR_ATmega328P__)
  if (_throttlePinNumber >= 0 && _throttlePinNumber <= 7) { //0-7 => PORTD: PCINT16-23
    _pcint = PCINT16 + _throttlePinNumber;
  } else if (_throttlePinNumber >= 8 && _throttlePinNumber <= 13) { //8-13 => PORTB: PCINT0-5 {
    _pcint = PCINT0 + (_throttlePinNumber - 8);
  } else if (_throttlePinNumber >= A0 && _throttlePinNumber <= A5) { //A0-A5 => PORTC: PCINT8-13
    _pcint = PCINT8 + (_throttlePinNumber - A0);
  } else
    return false;
#elif defined (__AVR_ATmega32U4__)
  switch(_throttlePinNumber) {
  case 8:
	  _pcint = PCINT4;
	  break;

  case 9:
	  _pcint = PCINT5;
	  break;

  case 10:
	  _pcint = PCINT6;
	  break;

  case 11:
	  _pcint = PCINT7;
	  break;

  case 14:
	  _pcint = PCINT3;
	  break;

  case 15:
	  _pcint = PCINT1;
	  break;

  case 16:
	  _pcint = PCINT2;
	  break;

  case 17:
	  _pcint = PCINT0;
	  break;
  default:
	  return false;
  }
#endif
  
  return true;
}

uint8_t CastleLinkLiveLib::_copyDataStructure(uint8_t index, CASTLE_RAW_DATA *dest ) {
  const uint8_t mask = _BV(index);
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
#if (LED_DISABLE == 0)
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

  /*if (nESC == 2) {
    EICRA = _BV(ISC01) | _BV(ISC11); // configure falling edge detection on INT0 and INT1
  } else {
    EICRA = _BV(ISC01); //configure falling edge detection on INT0*/
   
  	configEscINTs(nESC);

    escPinsHighMask = getEscPinsMask(nESC); //_BV(PORTD2);
    escPinsLowMask = ~ escPinsHighMask;

    extIntClearMask = getEscIntClearMask(nESC); //   _BV(INTF0);
    extIntEnableMask = getEscIntEnableMask(nESC); // _BV(INT0);
    extIntDisableMask = ~ extIntEnableMask;
  //}

  ESC_DDR |= escPinsHighMask; //set ESCs pins as outputs
  ESC_WRITE_PORT |= escPinsHighMask; //set ESCs pins high

  // set output compare match A of timer1 with number of ticks
  // corresponding to CASTLE_RESET_TIMEOUT
  TIMER_SET_COMPA (TIMER_RESET_TICKS);

  // enable output compare match A interrupt generation
  TIMER_ENABLE_COMPA();

  //init data structures
  for (int i = 0; i < _nESC; i++) _init_data_structure(i);

  if (_throttlePinNumber == GENERATE_THROTTLE) { // if auto-generating throttle...
    // set output compare match B with number of ticks
    // corresponding to TG_PERIOD
	  TIMER_SET_COMPB (TG_PERIOD_TICKS);

	  TIMER_ENABLE_COMPB(); //enable output compare match B interrupt generation
    
  } else {
    *_throttlePortModeReg &= ~ ( throttlePinMask ); //set throttle pin as input
    TIMER_ENABLE_OVF(); //enable timer overflow interrupt
  }

  /* 
   * SAFETY
   * rest of throttle receiving/generating initialization will not continue alone: 
   * program must explicitly call "throttleArm" function to continue.
   */

  _throttleMinTicks = throttleMin * (TIMER_FREQ / 1000000);
  _throttleMaxTicks = throttleMax * (TIMER_FREQ / 1000000);
  _throttleIntervalTicks = (throttleMax - throttleMin) * (TIMER_FREQ / 1000000);
  

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

boolean CastleLinkLiveLib::isThrottleArmed() {
	return (TIMER_IS_RUNNING() > 0);
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
  
  if (! (TIMER_IS_COMPB_ENABLED()) ) {
    TIMER_CLEAR();
    TIMER_SET_COMPB(TG_PERIOD_TICKS);
    TIMER_ENABLE_COMPB();
  }
  
  if (! IS_THROTTLE_PRESENT() ) {
    SET_THROTTLE_PRESENT();
    if (throttlePresenceHandler) 
      throttlePresenceHandler(1); 
  }
  
#if (LED_DISABLE == 0)
  ledMod = 100 - _throttle + 1;
#endif
}

//! [ESC data calculation details]
uint8_t CastleLinkLiveLib::getData( uint8_t index, CASTLE_ESC_DATA *o) {
  //uint16_t refTicks;
  uint16_t offTicks;
  uint8_t whichTemp;
  float value;

  CASTLE_RAW_DATA c;
  
  c.ticks[FRAME_REFERENCE] = 0;
  
  if (! _copyDataStructure(index, &c)) return false; //data was not ready
  
  //refTicks = c.ticks[FRAME_REFERENCE];
  /*if (c.ticks[FRAME_TEMP1] < c.ticks[FRAME_TEMP2]) {
    offTicks = c.ticks[FRAME_TEMP1];
    whichTemp = FRAME_TEMP2;
  } else {
    offTicks = c.ticks[FRAME_TEMP2];    
    whichTemp = FRAME_TEMP1;
  }*/

  offTicks = CLL_GET_OFFSET(c.ticks);
  whichTemp = CLL_GET_WHICH_TEMP(c.ticks);

  if (c.ticks[FRAME_REFERENCE] == 0) return false; //data was not ready
  
  for (int f = 1; f < DATA_FRAME_CNT; f++) {
    //value = ((float) (c.ticks[f] - offTicks)) / ((float) refTicks);
    value = CLL_BASE_VALUE(c.ticks, f, offTicks);

    switch(f) {
      case FRAME_VOLTAGE:
        //o->voltage = value * 20.0f;
        o->voltage = CLL_CALC_VOLTAGE(value);
        break;
      case FRAME_RIPPLE_VOLTAGE:
        //o->rippleVoltage = value * 4.0f;
        o->rippleVoltage = CLL_CALC_RIPPLE_VOLTAGE(value);
        break;
      case FRAME_CURRENT:
       // o->current = value * 50.0f;
        o->current = CLL_CALC_CURRENT(value);
        break;
      case FRAME_THROTTLE:
        //o->throttle = value;
        o->throttle = CLL_CALC_THROTTLE(value);
        break;
      case FRAME_OUTPUT_POWER:
        //o->outputPower = value * 0.2502f;
        o->outputPower = CLL_CALC_OUTPUT_POWER(value);
        break;
      case FRAME_RPM:
        //o->RPM = value * 20416.7f;
        o->RPM = CLL_CALC_RPM(value);
        break;
      case FRAME_BEC_VOLTAGE:
        //o->BECvoltage = value * 4.0f;
        o->BECvoltage = CLL_CALC_BEC_VOLTAGE(value);
        break;
      case FRAME_BEC_CURRENT:
        //o->BECcurrent = value * 4.0f;
        o->BECcurrent = CLL_CALC_BEC_CURRENT(value);
        break;
      case FRAME_TEMP1:
        //if (whichTemp == FRAME_TEMP1) o->temperature = value * 30.0f;
        if (whichTemp == FRAME_TEMP1) o->temperature = CLL_CALC_TEMP1(value);
        break;
      case FRAME_TEMP2:
        if (whichTemp == FRAME_TEMP2) {
        /*
           if (value > 3.9f)
             o->temperature = -40;
           else {
             float v = value * 63.8125f;
             o->temperature = 1.0f / (log(v * 10200.0f / (255 - v) / 10000.0f) / 3455.0f + 1.0f / 298.0f) - 273;
           }
         */
        	o->temperature = CLL_CALC_TEMP2(value);
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
  uint16_t ticks = TIMER_CNT;

  if (ticks == 0) return; //timer was stopped
  
  CASTLE_PRIV_DATA *d = &(data[index]);
  
  d->frameIdx++;
  
  d->ticks[d->frameIdx] = ticks;
  d->ticked = true;
  
  d->ready = (d->frameIdx == DATA_FRAME_CNT -1);
}

#ifdef ESC0_ISR
ISR(ESC0_ISR) {
  escInterruptHandler(0);
}
#endif

#ifdef ESC1_ISR
ISR(ESC1_ISR) {
  escInterruptHandler(1);
}
#endif

#ifdef ESC2_ISR
ISR(ESC2_ISR) {
  escInterruptHandler(2);
}
#endif

#ifdef ESC3_ISR
ISR(ESC3_ISR) {
  escInterruptHandler(3);
}
#endif

#ifdef ESC4_ISR
ISR(ESC4_ISR) {
  escInterruptHandler(4);
}
#endif


//=== PinChange interrupt handlers: get throttle signal
inline void throttleInterruptHandler(uint8_t pinStatus) {
  if ( pinStatus ) {  // throttle pulse start
     ESC_WRITE_PORT &= escPinsLowMask; //write LOW to ESCs pins
     TIMER_CLEAR();
#if (LED_DISABLE == 0)
     ledCnt++;
     ledCnt = ledCnt % ledMod;
     if (ledCnt == 0)
       LED_ON();
     else
       LED_OFF();
#endif
  } else {                            // throttle pulse end
     ESC_WRITE_PORT |= escPinsHighMask; //write high to ESCs pins
#if (LED_DISABLE == 0)
     uint16_t t = TCNT1;
#endif
     TIMER_CLEAR();

#if (LED_DISABLE == 0)
     if (t < _throttleMinTicks)
       t = 0;
     else
       t -= _throttleMinTicks;
     
     if (t >= _throttleIntervalTicks)
       ledMod = 1;
     else
       ledMod = 100 - ( t / ((float) (_throttleIntervalTicks)) * 100.0f) + 1;
#endif

     ESC_DDR &= escPinsLowMask; //set castle pins as inputs
#ifndef DISABLE_ALL_PULLUPS  
     ESC_WRITE_PORT &= escPinsLowMask; //write LOW to castle pins to disable pullups if not globally disabled
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
ISR(TIMER_COMPA_ISR) {
  EIMSK &= extIntDisableMask; //disable INT0/INT1 interrupt
  // timeout elapsed, so restore output mode for ESC pins in any case
#ifndef DISABLE_ALL_PULLUPS  
  ESC_WRITE_PORT |= escPinsHighMask; //write high to castle pind before switching to output if pullups are not globally disabled
#endif
  ESC_DDR |= escPinsHighMask;  //set castle pind to output

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

#if (LED_DISABLE == 0)
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
ISR(TIMER_COMPB_ISR) {
  ESC_TOGGLE_PORT |= escPinsHighMask; //toggle esc pins
  TIMER_CLEAR(); //clear timer
  
  if ( ! (ESC_WRITE_PORT & _BV(PORTD2)) ) {
    TIMER_SET_COMPB(throttlePulseHighTicks); //set COMPB to generate throttle pulse throttleTicks-long

#if (LED_DISABLE == 0)
    ledCnt++;
    ledCnt = ledCnt % ledMod;
    if (ledCnt == 0)
      LED_ON();
    else
      LED_OFF();
#endif
  } else {
    TIMER_SET_COMPB(throttlePulseLowTicks);
 
    ESC_DDR &= escPinsLowMask; //set castle pins as inputs
#ifndef DISABLE_ALL_PULLUPS  
    ESC_WRITE_PORT &= escPinsLowMask; //write LOW to castle pins to disable pullups if not globally disabled
#endif
    EIFR |= extIntClearMask; // clear INT0 INT1 flags before enabling
    EIMSK |= extIntEnableMask; //enable interrupts on INT0 and INT1

    throttleFailCnt++; //increase throttle failure counter: 
  }
  
  
  if (throttleFailCnt >= MAX_NO_THROTTLE_GEN) {
    TIMER_DISABLE_COMPB(); //disable interrupt generation
    ESC_WRITE_PORT |= escPinsHighMask; //pins high!
    ESC_DDR |= escPinsHighMask; //pins as output!
    throttleNotPresent();
  }
}

// overflow: won't fire if regular throttle signal (external) is present
ISR(TIMER_OVF_ISR) {
  throttleFailCnt++; //increase throttle failure counter

  if (throttleFailCnt >= MAX_OVERFLOW) {
    ESC_WRITE_PORT |= escPinsHighMask;
    ESC_DDR |= escPinsHighMask;
    throttleNotPresent();
    throttleFailCnt = 0; //reset throttle failure counter
  }
}

/*
 * Pre-istantiate object
 ****************************************************/
CastleLinkLiveLib CastleLinkLive;


