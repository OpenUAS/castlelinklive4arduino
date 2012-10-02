/*****************************************************************************
 *  CastleLinkLiveMonitor for windowed systems - DataLogger.java
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
 *  For further info, check http://code.google.com/p/java-comm-layer/
 *
 *  SVN: $Id$
 *  
 *****************************************************************************/

package it.picciux.castle.linklive.win;

import it.picciux.castle.linklive.CastleESC;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class DataLogger extends it.picciux.commlayer.DataLogger {
	private long startMs = -1;
	
	public DataLogger() {
		super();
	}

	public DataLogger(String logPath) {
		super(logPath);
	}

	@Override
	protected OutputStream openStream() {
		if (logPath.length() == 0) return null;
		
		FileOutputStream fos;
		
		try {
			fos = new FileOutputStream(logPath);
		} catch (FileNotFoundException e) {
			return null;
		}
		
		return fos;
	}

	@Override
	protected void setThreadData(Thread t) {
		t.setName("DataLogger");
	}

	public void logESC(CastleESC esc) {
		if (startMs == -1) startMs = System.currentTimeMillis();
		
		String line = 
				Double.toString(CastleLinkLiveMonitor.round((System.currentTimeMillis() - startMs) / 1000.0d, 1)) + "," + 
				Double.toString(CastleLinkLiveMonitor.round(esc.getVoltage(), 3)) + "," + 
				Double.toString(CastleLinkLiveMonitor.round(esc.getRippleVoltage(), 3)) + "," + 
				Double.toString(CastleLinkLiveMonitor.round(esc.getCurrent(), 3)) + "," +
				Long.toString(esc.getRPM()) + "," +
				Double.toString(CastleLinkLiveMonitor.round(esc.getThrottle(), 3)) + "," +
				Integer.toString(esc.getOutputPower()) + "," +
				Double.toString(CastleLinkLiveMonitor.round(esc.getTemperature(), 1)) + "," +
				Double.toString(CastleLinkLiveMonitor.round(esc.getBECvoltage(), 3)) + "," +
				Double.toString(CastleLinkLiveMonitor.round(esc.getBECcurrent(), 3)) + "\r\n";
				
		writeText(line);
	}
}
