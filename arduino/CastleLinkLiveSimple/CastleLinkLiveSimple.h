/*****************************************************************************
 *  CastleLinkLiveSimple - CastleLinkLiveSimple.h
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
 *
 *  SVN $Id: CastleLinkLiveSimple.h 35 2012-10-15 17:29:44Z picciux@gmail.com $
 ******************************************************************************/

 // Only modify this file to include
// - function definitions (prototypes)
// - include files
// - extern variable definitions
// In the appropriate section

#ifndef CastleLinkLiveSimple_H_
#define CastleLinkLiveSimple_H_
#include "WProgram.h"
//add your includes for the project CastleLinkLiveSimpleEx here
#include "HardwareSerial.h"
#include "pins_arduino.h"


//end of add your includes here
#ifdef __cplusplus
extern "C" {
#endif
void loop();
void setup();
#ifdef __cplusplus
} // extern "C"
#endif

//add your function definitions for the project CastleLinkLiveSimpleEx here




//Do not add code below this line
#endif /* CastleLinkLiveSimpleEx_H_ */
