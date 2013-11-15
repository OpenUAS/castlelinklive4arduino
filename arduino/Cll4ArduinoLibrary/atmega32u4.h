/*****************************************************************************
 *  CastleLinkLiveLib - atmega32u4.h
 *  Copyright (C) 2013 Matteo Piscitelli
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
 *****************************************************************************/

#ifndef ATMEGA32U4_H_
#define ATMEGA32U4_H_

#ifdef __AVR_ATmega32U4__

#include "pins_arduino.h"
#if defined(ARDUINO) && ARDUINO >= 100
#include "Arduino.h"
#else
#include "WProgram.h"
#endif

#define MAX_ESCS 5

/***************************************
 * TIMER macros
 ***************************************/
#define TIMER_PRESCALER 8 //overflows every 32.767ms - tick 0.5us @ 16MHz
#define TIMER_INIT() ( TCCR3A = 0 )
#define TIMER_START() (TCCR3B |=  _BV(CS11)) //prescaler 8

#define TIMER_CNT TCNT3

#define TIMER_CLEAR() ( TIMER_CNT = 0 )
#define TIMER_STOP() ( TCCR3B = 0 )
#define TIMER_IS_RUNNING() ( (TCCR3B & _BV(CS10)) || (TCCR3B & _BV(CS11)) || (TCCR3B & _BV(CS10)) )

//interrupt vectors
#define TIMER_COMPA_ISR TIMER3_COMPA_vect
#define TIMER_COMPB_ISR TIMER3_COMPB_vect
#define TIMER_OVF_ISR TIMER3_OVF_vect

#define TIMER_SET_COMPA( T ) ( OCR3A = T )
#define TIMER_SET_COMPB( T ) ( OCR3B = T )

#define TIMER_ENABLE_COMPA() ( TIMSK3 |= _BV(OCIE3A) )
#define TIMER_ENABLE_COMPB() ( TIMSK3 |= _BV(OCIE3B) )

#define TIMER_DISABLE_COMPA() ( TIMSK3 &= ~ (_BV(OCIE3A)) )
#define TIMER_DISABLE_COMPB() ( TIMSK3 &= ~ (_BV(OCIE3B)) )

#define TIMER_ENABLE_OVF() ( TIMSK3 |= _BV(TOIE3) )
#define TIMER_DISABLE_OVF() ( TIMSK3 &= ~ (_BV(TOIE3)) )

#define TIMER_IS_COMPA_ENABLED() ( TIMSK3 & _BV(OCIE3A) )
#define TIMER_IS_COMPB_ENABLED() ( TIMSK3 & _BV(OCIE3B) )
#define TIMER_IS_OVF_ENABLED() ( TIMSK3 & _BV(TOIE3) )

/***************************************
 * ESC PINs macros
 ***************************************/
#define ESC_READ_PORT PIND
#define ESC_TOGGLE_PORT PIND
#define ESC_WRITE_PORT PORTD
#define ESC_DDR DDRD

#define ESC0_ISR INT1_vect
#define ESC1_ISR INT0_vect
#define ESC2_ISR INT2_vect
#define ESC3_ISR INT3_vect
#define ESC4_ISR INT6_vect

void configEscINTs(uint8_t nescs) {
	EICRA |= 					_BV(ISC11); //pin 2
	if (nescs >= 2) EICRA |= 	_BV(ISC01); //pin 3
	if (nescs >= 3) EICRA |= 	_BV(ISC21); //pin 0
	if (nescs >= 4) EICRA |= 	_BV(ISC31); //pin 1
	if (nescs >= 5) EICRB |= 	_BV(ISC61); //pin 7
}

uint8_t getEscPinsMask(uint8_t nescs) {
	uint8_t ret = 			_BV(PORTD1); //pin 2
	if (nescs >= 2) ret |= 	_BV(PORTD0); //pin 3
	if (nescs >= 3) ret |= 	_BV(PORTD2); //pin 0
	if (nescs >= 4) ret |= 	_BV(PORTD3); //pin 1
	if (nescs == 5) ret |= 	_BV(PORTE6); //pin 7
	return ret;
}

uint8_t getEscIntClearMask(uint8_t nescs) {
	uint8_t ret = 			_BV(INTF1); //pin 2
	if (nescs >= 2) ret |= 	_BV(INTF0); //pin 3
	if (nescs >= 3) ret |= 	_BV(INTF2); //pin 0
	if (nescs >= 4) ret |= 	_BV(INTF3); //pin 1
	if (nescs == 5) ret |= 	_BV(INTF6); //pin 7
	return ret;
}

uint8_t getEscIntEnableMask(uint8_t nescs) {
	uint8_t ret = 			_BV(INT1); //pin 2
	if (nescs >= 2) ret |= 	_BV(INT0); //pin 3
	if (nescs >= 3) ret |= 	_BV(INT2); //pin 0
	if (nescs >= 4) ret |= 	_BV(INT3); //pin 1
	if (nescs >= 5) ret |= 	_BV(INT6); //pin 7
	return ret;
}


/***************************************
 * LED macros
 ***************************************/
#define LED_INIT() ( DDRC |= _BV(DDC7) )

#define LED_ON() ( PORTC |= _BV(PORTC7) )
#define LED_OFF() ( PORTC &= ~ _BV(PORTC7) )

#define LED_FLIP() ( PINC |= _BV(PINC7) )

#define LED_STATUS() ( PORTC & _BV(PORTC7) )

#endif /* __AVR_ATmega32U4__ */

#endif /* ATMEGA32U4_H_ */
