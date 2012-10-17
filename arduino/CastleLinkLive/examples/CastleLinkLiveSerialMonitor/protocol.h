/*****************************************************************************
 *  CastleLinkLiveSerialMonitor - protocol.h
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
 *  SVN $Id$
 *****************************************************************************/


/************** PROTOCOL DEFINES ******************/
#define OUT_DATA_HEADER_H                  	0xFF
#define OUT_DATA_HEADER_L                  	0xF0

#define ESC_ID_MASK                        	0x07 // 0000 0111
#define THROTTLE_PRESENT                   	0x08 // 0000 1000

#define OUT_RESPONSE_HEADER_H              	0x55
#define OUT_RESPONSE_HEADER_L              	0xAA

#define R_ACK                       	0x01
#define R_NACK                       0x00

#define CMD_HEADER 							0x00
#define CMD_NOOP                           	0x00
#define CMD_HELLO			   			   	0x01
#define CMD_SET_TMIN		           	   	0x02
#define CMD_SET_TMAX		           		0x03
#define CMD_SET_TMODE		           		0x04
#define CMD_SET_NESC		           		0x05
#define CMD_START		           			0x06
#define CMD_ARM				   				0x07
#define CMD_SET_THROTTLE                   	0x08
#define CMD_DISARM			   				0x09


#define STATUS_HELLO                          0
#define STATUS_CONF                           1
#define STATUS_STARTED                        2
#define STATUS_ARMED                          3

#define OUT_DATA_BUFSIZE                      25

#define QUEUE_LEN 10

typedef struct cmd_struct {
  uint8_t id;
  uint8_t l;
  uint8_t h;
} COMMAND;


const size_t commandSize = sizeof(COMMAND);

extern COMMAND cmdToProcess;
extern volatile uint8_t cmdProcessed;

/*
 * queueCommand is designed to be used in an ISR, so it's inlined and
 * checks for no other command waiting to be processed so to not interfere
 * with command processing code in main context.
 * Every command received while main context is busy processing a previous
 * command will be therefore discarded
 */
static inline void queueCommand(char *buffer) {
	if (cmdProcessed) {
		memcpy((&cmdToProcess), buffer, commandSize); //copy command to command-to-process place
		cmdProcessed = 0;
	}
}

/*
 * signal main context is done with previous command and ready to process another one
 */
static inline void commandProcessed() {
	cmdProcessed = 1;
}

COMMAND * getNextCommand();


