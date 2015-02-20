/*****************************************************************************
 *  CastleLinkLiveSerialMonitor - config.h 
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
 *  SVN $Id: config.h 30 2012-10-12 18:39:13Z picciux@gmail.com $
 *****************************************************************************/


/****************** CONFIGS ***********************/
#define SERIAL_BAUD_RATE            38400
#define LED                            13

/* 
 * you can safely change pin used to read throttle signal from
 * external equipment provided that you don't use any of the 
 * external interrupts pin (arduino pins 2 and 3) or the led
 * pin (arduino pin 13).
 */
#define THROTTLE_IN_PIN                 7


