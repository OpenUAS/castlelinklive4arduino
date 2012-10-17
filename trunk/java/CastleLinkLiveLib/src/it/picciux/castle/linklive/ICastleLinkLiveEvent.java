/*****************************************************************************
 *  CastleLinkLive library - ICastleLinkLiveEvent.java
 *  Copyright (C) 2012  Matteo Piscitelli
 *  E-mail: matteo@picciux.it
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
 *  SVN: $Id$
 *  
 *****************************************************************************/

package it.picciux.castle.linklive;

/**
 * Interface to be implemented by objects that want to receive
 * notifications from CastleLinkLive. 
 * Use {@link CastleLinkLive#setEventHandler(ICastleLinkLiveEvent)}
 * method passing an object implementing this interface to receive
 * events notification through it
 * @see CastleLinkLive
 * @author Matteo Piscitelli
 *
 */
public interface ICastleLinkLiveEvent {
	/**
	 * Event triggered when CastleLinkLive successfully parsed a completed set of data
	 * received from ESC interface
	 * @param index 0-based ESC index
	 * @param esc {@link CastleESC} object filled with updated data
	 */
	public void dataUpdated(int index, CastleESC esc);
	
	/**
	 * Event triggered when ESC interface looses throttle signal or detects a valid
	 * signal.
	 * @param present will be true if a valid throttle signal is present, false
	 * otherwise.
	 */
	public void throttlePresent(boolean present);
	
	/**
	 * Event triggered when a session with ESC interface is established/terminated
	 * @param connected true if a new session is established, false if active session is terminated
	 */
	public void connectionEvent(boolean connected);
	
	/**
	 * Event triggered when connection to ESC interface failed for some reason.
	 * @param reason will indicate the reason
	 */
	public void connectionError(String reason);
	
	/**
	 * Event triggered when ESC interface is succesfully armed/disarmed
	 * @param armed true if armed, false otherwise
	 */
	public void armedEvent(boolean armed);
}
