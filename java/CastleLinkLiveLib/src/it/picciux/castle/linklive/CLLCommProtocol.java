/*****************************************************************************
 *  CastleLinkLive library - CLLCommProtocol.java
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

//import java.util.logging.Logger;

/**
 * Class implementing communication protocol with CastleLinkLiveSerialMonitor
 * @author 00688263
 *
 */
public class CLLCommProtocol {
	public static final int FRAME_RESET 		= -1;
	public static final int FRAME_REFERENCE     =  0;
	public static final int FRAME_VOLTAGE       =  1;
	public static final int FRAME_RIPPLE_VOLTAGE=  2;
	public static final int FRAME_CURRENT       =  3;
	public static final int FRAME_THROTTLE      =  4;
	public static final int FRAME_OUTPUT_POWER  =  5;
	public static final int FRAME_RPM           =  6;
	public static final int FRAME_BEC_VOLTAGE   =  7;
	public static final int FRAME_BEC_CURRENT   =  8;
	public static final int FRAME_TEMP1         =  9;
	public static final int FRAME_TEMP2         = 10;	
	public static final int DATA_FRAME_CNT = 11;
	
	public static final int NO_ESC = -1;
	
	public static final int TYPE_ESCDATA = 0;
	public static final int TYPE_RESPONSE = 1;
	
	private int[] ticks = new int[DATA_FRAME_CNT];
	private int type = TYPE_ESCDATA;
	private int id = NO_ESC;
	private int response;
	private boolean throttlePresent = false;
	
	private int h_buffer;
	private int cnt = 0;
	private int checksum = 0;
	
	//private static Logger log = Logger.getLogger("it.picciux.castle.linklive.CLLCommProtocol");
	
	public int getTicks(int index) {
		if (index >= 0 && index < DATA_FRAME_CNT)
			return ticks[index];
		else
			return -1;
	}
	
	public boolean putByte(int b) {
		b = b & 0xFF;

		cnt++;
		
		if (cnt % 2 != 0) { //odd byte => MSB byte: save it for later
			h_buffer = b;
		} else {			// even byte => LSB byte: combine with buffer to get value
			if (cnt == 2) { 
				if ( (h_buffer == CastleLinkLive.HEADER_DATAIN_H) && (( b & CastleLinkLive.HEADER_DATAIN_MASK ) == CastleLinkLive.HEADER_DATAIN_L)) {
					type = TYPE_ESCDATA;
					id = (b & CastleLinkLive.ESC_ID_MASK); //store the ESC id
					throttlePresent = ( (b & CastleLinkLive.THROTTLE_PRESENT_MASK) > 0 ); //store throttle presence
					checksum = h_buffer;
					checksum ^= b;
				} else if ( (h_buffer == CastleLinkLive.HEADER_RESPONSE_H) && ((b & CastleLinkLive.HEADER_RESPONSE_MASK) == CastleLinkLive.HEADER_RESPONSE_L) )  {
					type = TYPE_RESPONSE;
					response = b & CastleLinkLive.RESPONSE_MASK;
					cnt = 0;
					checksum = 0;
					return true;
				} else { 
					//reset sequence!
					cnt = 0; 
					checksum = 0;
					return false;
				}
			} else if (cnt == DATA_FRAME_CNT * 2 + 4) { //final checksum!
				cnt = 0;
				int l_checksum = checksum;
				checksum = 0;
				
				return (l_checksum == h_buffer);
			} else {
				/*if (cnt == 4)
					checksum = h_buffer; //init checksum
				else*/
				checksum ^= h_buffer;
				checksum ^= b;
				ticks[cnt / 2 - 2] = (h_buffer << 8) + b;
			}
		}
		
		return false;
	}

	public int getId() {
		return id;
	}

	/**
	 * @return true if the ESC interface reports a valid throttle signal,
	 * false otherwise.
	 */
	public boolean isThrottlePresent() {
		return throttlePresent;
	}

	/**
	 * @return the response
	 */
	public int getResponse() {
		return response;
	}

	/**
	 * @return the type
	 */
	public int getType() {
		return type;
	}
	
}
