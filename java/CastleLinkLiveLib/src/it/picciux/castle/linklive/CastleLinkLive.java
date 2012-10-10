/*****************************************************************************
 *  CastleLinkLive library - CastleLinkLive.java
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

/*****************************************************************************
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
 ******************************************************************************/

package it.picciux.castle.linklive;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Vector;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Java library to exchange data with a Castle Creations ESC through an
 * Arduino board. For Arduino software to be used on the board and details
 * about the communication protocol, check CastleLinkLiveSerialMonitor in the 
 * "examples" sub-directory of the Arduino library available at 
 * http://code.google.com/p/castlelinklive4arduino/
 * @author Matteo Piscitelli
 */
public class CastleLinkLive {
	private class Command {
		public Command(int id, int value) {
			super();
			this.id = id;
			this.value = value;
		}
		
		public int id;
		public int value;
	}
	
	private class ThrottleThread extends Thread {
		private boolean keepRunning = true;
		private Vector<Command> cmdQueue = new Vector<Command>();
		
		public ThrottleThread() {
			super();
			setName("Throttle Thread");
		}
		
		public synchronized boolean isRunning() {
			return keepRunning;
		}
		
		public synchronized void terminate() {
			keepRunning = false;
			interrupt();
		}

		public synchronized void postCommand(Command command) {
			cmdQueue.add(command);
		}
		
		public synchronized Command commandInQueue() {
			if (cmdQueue.size() == 0) return null;
			
			Command c = cmdQueue.remove(0);
			return c;
		}
		
		@Override
		public void run() {
			while (isRunning()) {
				sendCommand(commandInQueue());
				
				if (isArmed() && throttleMode == SOFTWARE_THROTTLE)
					sendCommand(new Command(CMD_SET_THROTTLE, throttle));
				else
					sendCommand(new Command(CMD_NOOP, 0));
				
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}

			}
			
			if (eventHandler != null) eventHandler.connectionEvent(false);
		}	
	}

	private class StartThread extends Thread {
		private boolean keepRunning = true;
		private boolean keepWaiting = false;
		private boolean replied = false;
		private boolean ack = false;
		
		public StartThread() {
			super();
			setName("Start thread");
		}
		
		public synchronized void cancel() {
			keepRunning = false;
			interrupt();
		}

		private synchronized boolean sendAndWait(Command c) {
			keepWaiting = true;
			replied = false;
			
			if (! keepRunning) return false; 
				
			sendCommand(c);
			
			while (keepRunning && keepWaiting) {
				try {
					wait(5000);
					keepWaiting = false; 
				} catch (InterruptedException e) {
				}				
			}
			
			if (! replied) {
				keepRunning = false;
				log.warning("Hardware didn't reply. Failed!");
				startFailed(c, true);
			} else if (! ack) {
				log.warning("Hardware didn't ACK. Failed");
				startFailed(c, false);
			}
			
			return replied;
		}

		public synchronized void ack() {
			log.finer("ACK");
			replied = true;
			keepWaiting = false;
			ack = true;
			notify();
		}
		
		public synchronized void nack() {
			log.fine("NACK");
			replied = true;
			keepRunning = false;
			ack = false;
			notify();
		}
		
		@Override
		public void run() {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			
			if (!sendAndWait(new Command(CMD_HELLO, 0))) return;
			log.finer("HELLO ACK");

			if (!sendAndWait(new Command(CMD_SET_NESC, escs.size()))) return;
			log.finer("SET_NESC ACK");

			if (! sendAndWait(new Command(CMD_SET_TMIN, throttleMin))) return;
			log.finer("SET_TMIN ACK");

			if (! sendAndWait(new Command(CMD_SET_TMAX, throttleMax))) return;
			log.finer("SET_TMAX ACK");

			if (! sendAndWait(new Command(CMD_SET_TMODE, throttleMode))) return;
			log.finer("SET_TMODE ACK");

			if (! sendAndWait(new Command(CMD_START, 0))) return;
			log.finer("START ACK");
			
			startCompleted();
		}	
	}
	
	private static Logger log = Logger.getLogger("it.picciux.castle.linklive");
	private static final Level LOGLEVEL = Level.OFF;
	
	/**
	 * Integer constant indicating that the ESC interface has to generate throttle 
	 * signal for the ESC(s).
	 * Used as first parameter of {@link CastleLinkLive#start(int, int)}, or returned
	 * by {@link CastleLinkLive#getThrottleMode()} 
	 */
	public static final int SOFTWARE_THROTTLE = 1;
	
	/**
	 * Integer constant indicating that the ESC interface has to get the throttle signal
	 * from an external phisically connected RC-like equipment (tipically an RC receiver).
	 * Used as first parameter of {@link CastleLinkLive#start(int, int)}, or returned
	 * by {@link CastleLinkLive#getThrottleMode()} 
	 */
	public static final int EXTERNAL_THROTTLE = 0;
	
	public static final int DEFAULT_THROTTLE_MIN = 1000; //micro seconds
	public static final int DEFAULT_THROTTLE_MAX = 2000; //micro seconds

	public static final int ABSOLUTE_THROTTLE_MIN = 750; //micro seconds
	public static final int ABSOLUTE_THROTTLE_MAX = 2250; //micro seconds
	
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
	
	public static final int RESPONSE_ACK		= 0x01;
	public static final int RESPONSE_NACK		= 0x00;
	
	//private int nESC = 1;
	private Vector<CastleESC> escs = new Vector<CastleESC>();
	private CLLCommProtocol parser = new CLLCommProtocol();
	private ICastleLinkLiveEvent eventHandler = null;
	private int throttleMode = SOFTWARE_THROTTLE;
	private int throttle = 50;
	
	private int throttleMin = DEFAULT_THROTTLE_MIN;
	private int throttleMax = DEFAULT_THROTTLE_MAX;
	
	private boolean connected = false;
	private boolean armed = false;
	private boolean throttlePresent = false;
	
	private ThrottleThread throttleThread;
	private StartThread startThread;
	
	private OutputStream outStream;
	
	private void sendCommand(Command command) {
		if (command == null) return;

		log.finest("sendingCommand " + command.id + ": " + command.value);
		
		int [] buf = new int[] {
				0x00, 0x00, 0x00
		};
		
		int checksum = OUT_HEADER;
		buf[0] = command.id;
		buf[1] = command.value & 0xFF;
		buf[2] = (command.value >> 8) & 0xFF;
		
		try {
			outStream.write(checksum);
			for (int i = 0; i < buf.length; i++) {
				outStream.write(buf[i]);
				checksum ^= buf[i];
			}
			outStream.write(checksum);
		} catch (IOException e) {
			log.warning("Write exception: " + e.getMessage());
		}			
	}
	
	private void startFailed(Command command, boolean timeout) {
		startThread = null;
		String reason = "";
		
		if (! timeout) {
			switch(command.id) {
				case CMD_HELLO:
					reason = "ESC interface didn't respond to out handshake";
					break;
				case CMD_SET_NESC:
					reason = "Cannot set n.ESC to " + command.value; 
					break;
				case CMD_SET_TMAX:
					reason = "Cannot set throttle maximum to " + command.value; 
					break;
				case CMD_SET_TMIN:
					reason = "Cannot set throttle minimum to " + command.value; 
					break;
				case CMD_SET_TMODE:
					reason = "Cannot set throttle mode to " + command.value; 
					break;
				case CMD_START:
					reason = "ESC interface didn't start";
					break;
			}
		} else {
			reason = "ESC interface didn't respond to our ";
			switch(command.id) {
			case CMD_HELLO:
				reason += "handshake";
				break;
			case CMD_SET_NESC:
				reason += "set n. ESC"; 
				break;
			case CMD_SET_TMAX:
				reason += "set throttle maximum"; 
				break;
			case CMD_SET_TMIN:
				reason += "set throttle minimum"; 
				break;
			case CMD_SET_TMODE:
				reason += "set throttle mode"; 
				break;
			case CMD_START:
				reason += "start command"; 
				break;
			}
		}
		
		if (eventHandler != null) eventHandler.connectionError(reason);
	}
	
	private void startCompleted() {
		startThread = null;
		throttleThread = new ThrottleThread();
		throttleThread.start();		
		if (eventHandler != null) eventHandler.connectionEvent(true);
	}


	public CastleLinkLive() {
		log.getParent().setLevel(LOGLEVEL);
		
		Handler[] hs = log.getParent().getHandlers();
		for (int i = 0; i < hs.length; i++)
			hs[i].setLevel(LOGLEVEL);		
	}
	
	/**
	 * Sets desired throttle value. Only useful in {@link CastleLinkLive#SOFTWARE_THROTTLE} mode.
	 * @param throttle from 0 (idle/brake) to 100 (full throttle)
	 */
	public synchronized void setThrottle(int throttle) {
		if (throttle < 0) 
			this.throttle = 0;
		else if (throttle > 100) 
			this.throttle = 100;
		else
			this.throttle = throttle;
	}
	
	/**
	 * @return current throttle value. Only useful in {@link CastleLinkLive#SOFTWARE_THROTTLE} mode.
	 */
	public synchronized int getThrottle() {
		return this.throttle;
	}
	
	/**
	 * Puts a single byte (as an int) of data received by hardware interface in the receive
	 * buffer to be parsed by CastleLinkLive
	 * @param b data to put as an int (only least significant byte is considered)
	 * @throws InvalidDataException if data is not valid
	 */
	public void putData(int b) throws InvalidDataException {
		if (parser.putByte(b)) { //if parser completed parsing data... 
			
			switch (parser.getType()) {
			
				case CLLCommProtocol.TYPE_ESCDATA:
					int escId = parser.getId();

					if ( 
								escId > CLLCommProtocol.NO_ESC &&
								escs.get(escId).parseData(parser) && 
								eventHandler != null
					)
						eventHandler.dataUpdated(escId, escs.get(escId));
					
					// check changes in throttle presence
					if (throttlePresent != parser.isThrottlePresent()) {
						throttlePresent = parser.isThrottlePresent();
						if (eventHandler != null) eventHandler.throttlePresent(throttlePresent);
					}					
					break;
					
				case CLLCommProtocol.TYPE_RESPONSE:
					if ( (! connected) && (startThread != null) ) {
						if (parser.getResponse() == RESPONSE_ACK)
							startThread.ack();
						else
							startThread.nack();
					}
						
					break;
			}
			
		}
	}
	
	/**
	 * Puts an array of bytes (as integers) containing data received by hardware interface
	 * in the receive buffer to be parsed by CastleLinkLive.
	 * Only <b>count</b> array elements starting at element <b>offset</b> will 
	 * be put in the receive buffer
	 * @param bytes integer array containing data to put. Only least significant bytes are
	 * considered.
	 * @param offset the array element to start data from
	 * @param count the number of elements to put in receive buffer
	 * @throws InvalidDataException if data is not valid
	 */
	public void putData(int[] bytes, int offset, int count) throws InvalidDataException {
		for (int i = offset; i < count; i++)
			putData(bytes[i]);
	}
	
	/**
	 * Puts an array of bytes (as integers) containing data received by hardware interface
	 * in the receive buffer to be parsed by CastleLinkLive.
	 * @param bytes integer array containing data to put. Only least significant bytes are
	 * considered.
	 * @throws InvalidDataException if data is not valid
	 */
	public void putData(int[] bytes) throws InvalidDataException {
		putData(bytes, 0, bytes.length);
	}

	/**
	 * @return the number of Castle Creation ESCs connected to the hardware interface
	 */
	public int getnESC() {
		return escs.size();
	}
	
	/**
	 * Sets an ICastleLinkLiveEvent object as the event handler for
	 * status changed and data updated notifications
	 * @param eventHandler
	 * @see ICastleLinkLiveEvent
	 */
	public void setEventHandler(ICastleLinkLiveEvent eventHandler) {
		this.eventHandler = eventHandler;
	}

	/**
	 * 
	 * @return throttle pulse duration corresponding to min throttle (in microseconds)
	 */
	public int getThrottleMin() {
		return throttleMin;
	}

	/**
	 * Sets maximum pulse-duration for max throttle (in microseconds).
	 * 
	 * @param throttleMin
	 * @throws InvalidThrottleLimitException if throttleMin specifies a value less than
	 * minimum allowed
	 */
	public void setThrottleMin(int throttleMin) throws InvalidThrottleLimitException {
		if (throttleMin < ABSOLUTE_THROTTLE_MIN) 
			throw new InvalidThrottleLimitException(
					"Throttle min pulse duration cannot be less than " +
					ABSOLUTE_THROTTLE_MIN + " microseconds"
			);

		this.throttleMin = throttleMin;
	}

	/**
	 * @return throttle pulse duration corresponding to max throttle (in microseconds)
	 */
	public int getThrottleMax() {
		return throttleMax;
	}

	/**
	 * Sets maximum pulse-duration for max throttle (in microseconds).
	 * 
	 * @param throttleMax
	 * @throws InvalidThrottleLimitException if throttleMax specifies a value greater than
	 * maximum allowed
	 */
	public void setThrottleMax(int throttleMax) throws InvalidThrottleLimitException {
		if (throttleMax > ABSOLUTE_THROTTLE_MAX) 
			throw new InvalidThrottleLimitException(
					"Throttle max pulse duration cannot be greater than " +
					ABSOLUTE_THROTTLE_MAX + " microseconds"
			);

		this.throttleMax = throttleMax;
	}

	/**
	 * @return whether is generating throttle or considering external throttle.
	 */
	public boolean isArmed() {
		return armed;
	}

	/**
	 * Enables the ESC interface to start generating/managing throttle signal,
	 * whether it is software generated or external
	 */
	public void arm() {
		if (armed) return;
		
		throttle = 50;
		armed = true;
		
		throttleThread.postCommand(new Command(CMD_ARM, 0));
	}

	/**
	 * Asks the ESC interface to stop generating/managing throttle signal,
	 * whether it is software generated or external
	 */
	public void disarm() {
		if (! armed) return;
		
		armed = false;
		
		if (throttleThread != null)
			throttleThread.postCommand(new Command(CMD_DISARM, 0));
	}
	
	/**
	 * Starts a new session: tries to handshake with ESC interface. It's program
	 * responsibility to give CastleLinkLive an {@link OutputStream} to talk to
	 * ESC interface ({@link CastleLinkLive#setOutStream(OutputStream)}), and to 
	 * feed back data from the interface itself through any of {@link CastleLinkLive#putData(int)}, 
	 * {@link CastleLinkLive#putData(int[])} or {@link CastleLinkLive#putData(int[], int, int)}.
	 * Function parameters set the interface to generate throttle
	 * or to take an external throttle, for the specified number of ESC(s).
	 * @param throttleMode can be {@link CastleLinkLive#SOFTWARE_THROTTLE} or {@link CastleLinkLive#EXTERNAL_THROTTLE}
	 * @param nESC number of ESCs the interface is connected to
	 * @return true
	 */
	public boolean start(int throttleMode, int nESC) {
		this.throttleMode = throttleMode; //TODO condition param
		escs.clear();
		for (int i = 0; i < nESC; i++)
			escs.add(new CastleESC());
		
		startThread = new StartThread();
		startThread.start();
		
		return true;
	}
	
	/**
	 * Cancels a session start attempt.
	 */
	public void cancelStart() {
		if (startThread != null) startThread.cancel();
	}
		
	/**
	 * Terminates an already established session with ESC interface (it's
	 * program responsibility to close underlying streams for communication
	 * with ESC interface)
	 */
	public void stop() {
		if (throttleThread != null && throttleThread.isRunning()) {
			if (armed) {
				throttleThread.postCommand(new Command(CMD_DISARM, 0));
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
				}
				armed = false;
			}
			throttleThread.terminate();
			throttleThread = null;
			throttlePresent = false;
		}
		
		//throttle thread signals disconnection to eventHandler at thread termination
	}
	
	/**
	 * @return the {@link OutputStream} that CastleLinkLive is using to send data to ESC interface
	 */
	public OutputStream getOutStream() {
		return outStream;
	}

	/**
	 * @param outStream sets the {@link OutputStream} to be used by CastleLinkLive to send data to ESC interface
	 */
	public void setOutStream(OutputStream outStream) {
		this.outStream = outStream;
	}
	
	/**
	 * Returns the CastleESC object identified by whichESC
	 * @param whichESC zero-based integer specifying which ESC to return
	 * @return CastleESC corresponding to whichESC, or null if whichESC is out of bounds.
	 * @see CastleESC
	 */
	public CastleESC getESC(int whichESC) {
		if ( (whichESC < 0) || (whichESC > escs.size() - 1 ) )
			return null;
		else
			return escs.get(whichESC);
	}

	/**
	 * @return the throttle mode active on connected hardware interface.
	 * Possible values are:<br />
	 * - {@link CastleLinkLive#SOFTWARE_THROTTLE} for hardware interface software-generated throttle signal<br />
	 * - {@link CastleLinkLive#EXTERNAL_THROTTLE} for throttle signal generated by external RC equipment
	 * connected to hardware interface
	 */
	public int getThrottleMode() {
		return throttleMode;
	}
	
}
