/*****************************************************************************
 *  CastleLinkLive library - CastleLinkLiveException.java
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

public class CastleLinkLiveException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8941028425017208915L;

	public CastleLinkLiveException() {
	}

	public CastleLinkLiveException(String message) {
		super(message);
	}

	public CastleLinkLiveException(Throwable cause) {
		super(cause);
	}

	public CastleLinkLiveException(String message, Throwable cause) {
		super(message, cause);
	}

}
