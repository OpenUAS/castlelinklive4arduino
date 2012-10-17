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
 *  SVN $Id: protocol.cpp 50 2012-10-17 15:11:47Z picciux@gmail.com $
 *****************************************************************************/

#include "WProgram.h"
#include "USART.h"
#include "protocol.h"

COMMAND cmdToProcess; //store for the to-be-processed command
volatile uint8_t cmdProcessed = 1; //flag to indicate processed/still-to-process

COMMAND * getNextCommand() {
  //COMMAND *ret = NULL;

  if (! cmdProcessed) { //there's a command to process
	  return &cmdToProcess;
  } else
	  return NULL; //nothing to process
}


