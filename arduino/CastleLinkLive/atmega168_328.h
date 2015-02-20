/*****************************************************************************
 *  CastleLinkLiveLib - atmega168_328.h
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

#ifndef ATMEGA168_328_H_
#define ATMEGA168_328_H_

#if defined (__AVR_ATmega168__) || defined(__AVR_ATmega328P__)

#include "pins_arduino.h"
#if defined(ARDUINO) && ARDUINO >= 100
#include "Arduino.h"
#else
#include "WProgram.h"
#endif

#define MAX_ESCS 2

/***************************************
 * TIMER macros
 ***************************************/
#define TIMER_PRESCALER 8 //overflows every 32.767ms - tick 0.5us @ 16MHz
#define TIMER_INIT() ( TCCR1A = 0 )
#define TIMER_START() (TCCR1B |=  _BV(CS11)) //prescaler 8

#define TIMER_CNT TCNT1

#define TIMER_CLEAR() ( TIMER_CNT = 0 )
#define TIMER_STOP() ( TCCR1B = 0 )
#define TIMER_IS_RUNNING() ( (TCCR1B & _BV(CS10)) || (TCCR1B & _BV(CS11)) || (TCCR1B & _BV(CS10)) )

//interrupt vectors
#define TIMER_COMPA_ISR TIMER1_COMPA_vect
#define TIMER_COMPB_ISR TIMER1_COMPB_vect
#define TIMER_OVF_ISR TIMER1_OVF_vect

#define TIMER_SET_COMPA( T ) ( OCR1A = T )
#define TIMER_SET_COMPB( T ) ( OCR1B = T )

#define TIMER_ENABLE_COMPA() ( TIMSK1 |= _BV(OCIE1A) )
#define TIMER_ENABLE_COMPB() ( TIMSK1 |= _BV(OCIE1B) )

#define TIMER_DISABLE_COMPA() ( TIMSK1 &= ~ (_BV(OCIE1A)) )
#define TIMER_DISABLE_COMPB() ( TIMSK1 &= ~ (_BV(OCIE1B)) )

#define TIMER_ENABLE_OVF() ( TIMSK1 |= _BV(TOIE1) )
#define TIMER_DISABLE_OVF() ( TIMSK1 &= ~ (_BV(TOIE1)) )

#define TIMER_IS_COMPA_ENABLED() ( TIMSK1 & _BV(OCIE1A) )
#define TIMER_IS_COMPB_ENABLED() ( TIMSK1 & _BV(OCIE1B) )
#define TIMER_IS_OVF_ENABLED() ( TIMSK1 & _BV(TOIE1) )


/***************************************
 * ESC PINs macros and config functions
 ***************************************/
#define ESC_READ_PORT PIND
#define ESC_TOGGLE_PORT PIND
#define ESC_WRITE_PORT PORTD
#define ESC_DDR DDRD

#define ESC0_ISR INT0_vect
#define ESC1_ISR INT1_vect

void configEscINTs(uint8_t nescs) {
	EICRA |= _BV(ISC01);
	if (nescs == 2)
		EICRA |= _BV(ISC11);
}

uint8_t getEscPinsMask(uint8_t nescs) {
	uint8_t ret = _BV(PORTD2);
	if (nescs == 2) ret |= _BV(PORTD3);
	return ret;
}

uint8_t getEscIntClearMask(uint8_t nescs) {
	uint8_t ret = _BV(INTF0);
	if (nescs == 2) ret |= _BV(INTF1);
	return ret;
}

uint8_t getEscIntEnableMask(uint8_t nescs) {
	uint8_t ret = _BV(INT0);
	if (nescs == 2) ret |= _BV(INT1);
	return ret;
}


/***************************************
 * LED macros
 ***************************************/
#define LED_INIT() ( DDRB |= _BV(DDB5) )

#define LED_ON() ( PORTB |= _BV(PORTB5) )
#define LED_OFF() ( PORTB &= ~ _BV(PORTB5) )

#define LED_FLIP() ( PINB |= _BV(PINB5) )

#define LED_STATUS() ( PORTB & _BV(PORTB5) )

#endif /* defined (__AVR_ATmega168__) || defined(__AVR_ATmega328P__) */

#endif /* ATMEGA168_328_H_ */
