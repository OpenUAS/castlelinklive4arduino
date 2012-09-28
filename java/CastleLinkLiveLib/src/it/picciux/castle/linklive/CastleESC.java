/* SVN: $Id$ */

/*****************************************************************************
 *  CastleLinkLive library - CastleESC.java
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
 *****************************************************************************/

package it.picciux.castle.linklive;

/**
 * Class representing a Castle Creations ESC
 * @author Matteo Piscitelli
 *
 */
public class CastleESC {
	private double voltage;
	private double rippleVoltage;
	private double current;
	private double throttle;
	private int outputPower;
	private long electricalRPM;
	private double BECvoltage;
	private double BECcurrent;
	private double temperature;
	private int rpmDivider = 1;
	private boolean updated = false;
	
	/**
	 * Class Constructor
	 */
	public CastleESC() {
	}

	/**
	 * Class Constructor.
	 * This is equivalent to call {@link CastleESC#setMotorPoles(int)} after
	 * creating the object.
	 * @param motorPoles specifies motor poles number
	 */
	public CastleESC(int motorPoles) {
		setMotorPoles(motorPoles);
	}
	
	/**
	 * Sets the motor poles number for the electric motor controlled by this ESC:
	 * since the ESC reports the electrical RPM, it's necessary to know the 
	 * motor poles to calculate shaft/prop RPM (not counting a prop gear if installed).
	 * If not set, simply electrical RPM will be reported. 
	 * @param motorPoles the motor poles number
	 * @return true if motorPoles is even. False otherwise.
	 */
	public boolean setMotorPoles(int motorPoles) {
		if (motorPoles % 2 != 0) return false;
		rpmDivider = motorPoles / 2;
		return true;
	}
	
	/**
	 * 
	 * @return the configured motor poles number to calc shaft RPM.
	 */
	public int getMotorPoles() {
		return rpmDivider * 2;
	}
	
	/**
	 * @return battery voltage as reported by ESC (in Volts)
	 */
	public double getVoltage() {
		return voltage;
	}

	/**
	 * @return battery ripple voltage as reported by ESC (in Volts)
	 */
	public double getRippleVoltage() {
		return rippleVoltage;
	}

	/**
	 * @return current flowing through power system, as reported by ESC (in Amperes)
	 */
	public double getCurrent() {
		return current;
	}

	/**
	 * @return throttle pulse duration as reported by ESC (in microseconds)
	 */
	public double getThrottle() {
		return throttle;
	}

	/**
	 * @return the power level the ESC is driving the motor (percentage: 0-100)
	 */
	public int getOutputPower() {
		return outputPower;
	}

	/**
	 * @return the electrical RPM as reported by ESC (not counting motor poles number)
	 */
	public long getElectricalRPM() {
		return electricalRPM;
	}

	/**
	 * @return the shaft RPM as reported by ESC (if motor poles number is set correctly)
	 */
	public long getRPM() {
		return electricalRPM / rpmDivider;
	}
	
	/**
	 * @return the BEC voltage as reported by ESC (in Volts)
	 */
	public double getBECvoltage() {
		return BECvoltage;
	}

	/**
	 * @return the BEC current as reported by ESC (in Amperes)
	 */
	public double getBECcurrent() {
		return BECcurrent;
	}

	/**
	 * @return the ESC temperature, as reported by ESC itself (in degree Celsius)
	 */
	public double getTemperature() {
		return temperature;
	}

	private double checkValue(double oldVal, double newVal) {
		if (oldVal != newVal) {
			updated = true;
			return newVal;
		} else
			return oldVal;
	}
	
	private int checkValue(int oldVal, int newVal) {
		if (oldVal != newVal) {
			updated = true;
			return newVal;
		} else
			return oldVal;		
	}

	private long checkValue(long oldVal, long newVal) {
		if (oldVal != newVal) {
			updated = true;
			return newVal;
		} else
			return oldVal;		
	}
	
	/**
	 * Gets a {@link CLLCommProtocol} object as a parameter and will calculate
	 * readable ESC data basing on its data.
	 * @param data a CLLCommProtocol object
	 * @return true if some data in the ESC is updated, false otherwise.
	 * @throws InvalidDataException if data contained in DataParser is 
	 * not valid.
	 * @see CLLCommProtocol
	 */
	public boolean parseData(CLLCommProtocol data) throws InvalidDataException {
		int ref = data.getTicks(CLLCommProtocol.FRAME_REFERENCE);
		int offset = Math.min(
				data.getTicks(CLLCommProtocol.FRAME_TEMP1), 
				data.getTicks(CLLCommProtocol.FRAME_TEMP2) 
		);
		
		if (ref == 0) throw new InvalidDataException("Invalid data: no reference!");
		
		updated = false;

		for (int f = 1; f < CLLCommProtocol.DATA_FRAME_CNT; f++) {
			int ticks = data.getTicks(f);
			
			if (ticks == 0) continue; //no data?
			
			ticks -= offset;
			
			double value = ((double) ticks) / ((double) ref);
			
			int temp1Ticks = 0;
			
			switch(f) {
				case CLLCommProtocol.FRAME_VOLTAGE:
					voltage = checkValue(voltage, value * 20.0d);
					break;
				case CLLCommProtocol.FRAME_RIPPLE_VOLTAGE:
					rippleVoltage = checkValue(rippleVoltage, value * 4.0d);
					break;
				case CLLCommProtocol.FRAME_CURRENT:
					current = checkValue(current, value * 50.0d);
					break;
				case CLLCommProtocol.FRAME_THROTTLE:
					throttle = checkValue(current, value);
					break;
				case CLLCommProtocol.FRAME_OUTPUT_POWER:
					outputPower = checkValue(outputPower, (int) Math.round(value * 0.2502d * 100.0d));
					break;
				case CLLCommProtocol.FRAME_RPM:
					electricalRPM = checkValue(electricalRPM, Math.round(value * 20416.7d));
					break;
				case CLLCommProtocol.FRAME_BEC_VOLTAGE:
					BECvoltage = checkValue(BECvoltage, value * 4.0d);
					break;
				case CLLCommProtocol.FRAME_BEC_CURRENT:
					BECcurrent = checkValue(BECcurrent, value * 4.0d);
					break;
				case CLLCommProtocol.FRAME_TEMP1:
					temp1Ticks = ticks;
					temperature = checkValue(temperature, value * 30.0d);
					break;
				case CLLCommProtocol.FRAME_TEMP2:
					if (ticks > temp1Ticks) {
						if (value > 3.9d) 
							temperature = checkValue(temperature, -40);
						else {
							double d = value * 63.8125d;
							temperature = checkValue(temperature, 1.0d / (Math.log(d * 10200d / (255 - d) / 10000.0d) / 3455.0d + 1.0d / 298.0d) - 273);
						}
					}
					break;
			}
		}
		
		return updated;
	}

	
}
