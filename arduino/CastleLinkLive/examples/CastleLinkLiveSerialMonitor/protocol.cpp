/*****************************************************************************
 *  CastleLinkLiveSerialMonitor - protocol.cpp
 *  Copyright (C) 2012 Matteo Piscitelli
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

#include "WProgram.h"
#include "USART.h"
#include "protocol.h"

COMMAND_QUEUE cmdQueue;

COMMAND c; //out-of-queue command buffer to be processed

/*
 * getNextCommand is designed to be used in main code, so has to make sure no-one
 * (the USART ISR in this case) is accessing the data
 */
COMMAND * getNextCommand() {
  uart_disable_interrupt(); //temporarily disable uart interrupt while we extract first command

  if (cmdQueue.head == cmdQueue.tail) {
  	  uart_enable_interrupt();
  	  return NULL; //queue is empty: nothing to do
  }

  memcpy(&c, &(cmdQueue.queue[cmdQueue.tail]), commandSize); //extract first command to process
  cmdQueue.tail = (cmdQueue.tail + 1) % QUEUE_LEN; //advance tail
  uart_enable_interrupt(); //done: re-enable uart interrupt

  return &c; //return the command for further processing
}

