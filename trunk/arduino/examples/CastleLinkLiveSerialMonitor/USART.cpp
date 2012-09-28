/* SVN $Id$ */

/*****************************************************************************
 *  CastleLinkLiveSerialMonitor - USART.cpp
 *  Copyright (C) 2012  Matteo Piscitelli
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
 *****************************************************************************/

#include "WProgram.h"
#include "USART.h"
#include "protocol.h"

char buffer[BUFSIZE];
int8_t bufcnt = -1;
uint8_t checksum;

COMMAND cmd;
COMMAND *nextCmd = NULL;
COMMAND *lastCmd = NULL;

volatile uint8_t cmdQueueBusy = 0;

void tx(char data) {
  while( ( UCSR0A & _BV(UDRE0) )==0 );
  UDR0 = data;
}

void txstr(char *str) {
  char *p = str;
  
  while( *p != '\0' ) {
    tx(*p);
    p++;
  }
}

void txbuf(uint8_t *b, uint16_t count) {
   for (int i = 0; i < count; i++) 
     tx( *(b + i) );
}

unsigned char rx(void) {
  while( ( UCSR0A & _BV(RXC0) )==0 );
  return UDR0;
}


unsigned char rx_nb(void) {
  unsigned char retval = 0;
  if( ( UCSR0A & _BV(RXC0) ) != 0 ) {
    retval = UDR0;
  }
  return retval;
}

/*
 * From arduino hardware_serial.cpp
 */
void uart_init(uint16_t baudrate) {
  short use_u2x = 1;
  uint16_t baud_val;
        
#if F_CPU == 16000000UL
  if (baudrate == 57600) {
      use_u2x = 0;
  } 
#endif       

  UCSR0C = /*(0<<USBS0 ) |*/ _BV(UCSZ01) | _BV(UCSZ00);
	
  if (use_u2x) {
    UCSR0A = _BV(U2X0);
    baud_val = (F_CPU / 4 / baudrate - 1) / 2;
  } else {
    UCSR0A = 0;
    baud_val = (F_CPU / 8 / baudrate - 1) / 2;          
  }

  UBRR0L = baud_val;
  UBRR0H = baud_val >> 8;		

  UCSR0B = _BV(RXEN0) | _BV(TXEN0);
	
}

void uart_enable_interrupt() {
  // Enable UART RX interrupts
  UCSR0B |= _BV(RXCIE0);  
}

void uart_disable_interrupt() {
  // Disable UART RX interrupts
  UCSR0B &= ~( _BV(RXCIE0) );    
}

void uart_flush_rxbuffer() {
  unsigned char dummy;
  while ( UCSR0A & _BV(RXC0) ) dummy = UDR0; 
}

/*
 * USART RX Interrupt
 */
ISR(USART_RX_vect /*, ISR_NOBLOCK */) {
  uint8_t c = UDR0;

  if ( bufcnt == -1 ) {
    if (c == CMD_HEADER) bufcnt = 0; //header ok: start filling buffer
    return; 
  } else if (bufcnt == BUFSIZE) { //buffer full: check checksum
    if (checksum == c) { //checksum ok: queue the command

      COMMAND *n = (COMMAND *) malloc(sizeof(COMMAND));
      n->next = NULL;
      //memcpy(&cmd, buffer, BUFSIZE); //post command
      memcpy(n, &buffer, BUFSIZE); //post command
      
      cmdQueueBusy = 1;
      
      if (lastCmd == NULL) {
        nextCmd = n;
        lastCmd = n; 
      } else {
        lastCmd->next = n;
        lastCmd = n;
      }      
      
      cmdQueueBusy = 0;
    }

    bufcnt = -1; //reset buffer
    return;
  }
  
  //filling buffer
  buffer[bufcnt++] = c;
  if (bufcnt == 1)
    checksum  = c;
  else
    checksum ^= c;
  
}
