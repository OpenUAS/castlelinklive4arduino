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

/**
 * Class implementing communication protocol with CastleLinkLiveSerialMonitor.
 * This class is not meant to be instantiated by library users: it's used internally
 * by {@link CastleLinkLive}
 * @author Matteo Piscitelli
 * @see CastleLinkLive
 */
public class CLLCommProtocol {
	/* DATA FRAME IDs */
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
	
	/* DATA TYPES */
	public static final int TYPE_ESCDATA = 0;
	public static final int TYPE_RESPONSE = 1;
	
	/* DATA TRANSMISSION HEADERS AND MASKS */
	public static final int HEADER_DATAIN_H		    = 0xFF;
	
	/* LS byte in header is actually header and data:
	 *  bit 0-2: ESC id (1->7)
	 *  bit 3  : external throttle presence (1 present, 0 not present/invalid)
	 */
	public static final int HEADER_DATAIN_L			= 0xF0; // 1111 0000
	
	public static final int HEADER_DATAIN_MASK		= 0xF0; // 1111 0000
	public static final int ESC_ID_MASK				= 0x07; // 0000 0111
	public static final int THROTTLE_PRESENT_MASK	= 0x08; // 0000 1000
	
	public static final int HEADER_RESPONSE_H		= 0x55; // 0101 0101
	public static final int HEADER_RESPONSE_L		= 0xAA; // 1010 1010
	
	public static final int HEADER_RESPONSE_MASK	= 0xFE; // 1111 1110
	public static final int RESPONSE_MASK			= 0x01; // 0000 0001
	
	public static final int OUT_HEADER			= 0x00;

	/* COMMAND IDENTIFIERS */
	public static final int CMD_NOOP			= 0x00;
	public static final int CMD_HELLO			= 0x01;
	public static final int CMD_SET_TMIN		= 0x02;
	public static final int CMD_SET_TMAX		= 0x03;
	public static final int CMD_SET_TMODE		= 0x04;
	public static final int CMD_SET_NESC		= 0x05;
	public static final int CMD_START			= 0x06;
	public static final int CMD_ARM				= 0x07;
	public static final int CMD_SET_THROTTLE	= 0x08;
	public static final int CMD_DISARM			= 0x09;
	
	/* RESPONSE ACK/NACK VALUES */
	public static final int RESPONSE_ACK		= 0x01;
	public static final int RESPONSE_NACK		= 0x00;
	
	
	private int[] ticks = new int[DATA_FRAME_CNT];
	private int type = TYPE_ESCDATA;
	private int id = NO_ESC;
	private int response;
	private boolean throttlePresent = false;
	
	private int h_buffer;
	private int cnt = 0;
	private int checksum = 0;
	
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

			if (cnt == DATA_FRAME_CNT * 2 + 3) { //DATA block completed
				cnt = 0;
				boolean checksum_ok = (checksum == h_buffer);
				checksum = 0;
				return checksum_ok;
			}			
		
		} else {			// even byte => LSB byte: combine with buffer to get value
			if (cnt == 2) {
				if ( (h_buffer == HEADER_DATAIN_H) && (( b & HEADER_DATAIN_MASK ) == HEADER_DATAIN_L)) {
					type = TYPE_ESCDATA;
					id = (b & ESC_ID_MASK); //store the ESC id
					throttlePresent = ( (b & THROTTLE_PRESENT_MASK) > 0 ); //store throttle presence
					checksum = h_buffer;
					checksum ^= b;
				} else if ( (h_buffer == HEADER_RESPONSE_H) && ((b & HEADER_RESPONSE_MASK) == HEADER_RESPONSE_L) )  {
					type = TYPE_RESPONSE;
					response = b & RESPONSE_MASK;
					cnt = 0;
					checksum = 0;
					return true;
				} else { 
					//reset sequence!
					cnt = 0; 
					checksum = 0;
					return false;
				}
			} else {
				checksum ^= h_buffer;
				checksum ^= b;
				ticks[cnt / 2 - 2] = (h_buffer << 8) + b;
			}
		}
		
		return false;
	}

	/**
	 * @return the 0-based index of the ESC whose data is parsed last
	 */
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
	 * @return last response received from the ESC interface
	 */
	public int getResponse() {
		return response;
	}

	/**
	 * @return the type of data the parser finished parsing last: 
	 * {@link CLLCommProtocol#TYPE_ESCDATA} if data was ESC telemetry data or
	 * {@link CLLCommProtocol#TYPE_RESPONSE} if data was a response to a previously
	 * issued command
	 * @see CastleLinkLive 
	 */
	public int getType() {
		return type;
	}
	
}
