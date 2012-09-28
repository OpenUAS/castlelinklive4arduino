
/*****************************************************************************
 *  CastleLinkLiveSerialMonitor - USART.h
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

#ifndef USART_H
#define USART_H

void txbuf(uint8_t *b, uint16_t count);
void txstr(char *str);
void tx(char data);
unsigned char rx(void);
unsigned char rx_nb(void);
void uart_flush_rxbuffer();
void uart_init(uint16_t baudrate);
void uart_enable_interrupt();
void uart_disable_interrupt();


#endif
