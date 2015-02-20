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

/*
import java.util.logging.Handler;
import java.util.logging.Level;*/
import it.picciux.commlayer.log.Logger;
import it.picciux.commlayer.log.LoggerException;

/**
 * Java library to exchange data with a Castle Creations ESC through an
 * Arduino board. For Arduino software to be used on the board and details
 * about the communication protocol, check CastleLinkLiveSerialMonitor in the 
 * "examples" sub-directory of the Arduino library available at 
 * http://code.google.com/p/castlelinklive4arduino/
 * @author Matteo Piscitelli
 */
public class CastleLinkLive {
	/**
	 * Internal class to hold commands to ESC interface
	 * @author Matteo Piscitelli
	 *
	 */
	private class Command {
		public Command(int id, int value) {
			super();
			this.id = id;
			this.value = value;
		}
		
		/**
		 * Command id
		 * @see CLLCommProtocol
		 */
		public int id;
		
		/**
		 * Command value (if any)
		 */
		public int value;
	}
	
	/**
	 * Thread to command the ESC interface
	 * @author Matteo Piscitelli
	 *
	 */
	private class ESCInterfaceThread extends Thread {
		private boolean keepRunning = true;
		private boolean keepWaiting = false;
		private boolean replied = false;
		private boolean ack = false;		
		private Vector<Command> cmdQueue = new Vector<Command>();
		private int waitingCommandID = CMD_NONE;
		
		private static final int START_TIMEOUT = 3000;
		private static final int RUN_TIMEOUT = 2000;
		
		private static final int CMD_NONE = -1;
		
		public ESCInterfaceThread() {
			super();
			setName("Throttle Thread");
		}
		
		public synchronized boolean isRunning() {
			return keepRunning;
		}
		
		public synchronized void terminate() {
			keepRunning = false;
		}

		/* NOT NEEDED ATM
		public synchronized void forceTermination() {
			keepRunning = false;
			
			//force replied to avoid to notify failure when thread is terminated 
			//while it's waiting for a reply
			replied = true; 
			interrupt();			
		}
		*/
		
		public synchronized void cancel() {
            keepRunning = false;
            interrupt();
		}
		
		public synchronized void ack() {
			log.finer("ACK " + waitingCommandID);
			if (waitingCommandID == CLLCommProtocol.CMD_ARM) setArmed(true);
			if (waitingCommandID == CLLCommProtocol.CMD_DISARM) setArmed(false);
			replied = true;
			keepWaiting = false;
			ack = true;
			notify();
		}
		
		public synchronized void nack() {
			log.finer("NACK " + waitingCommandID);
			replied = true;
			keepRunning = false;
			ack = false;
			notify();
		}
		
		
		public synchronized void postCommand(Command command) {
			cmdQueue.add(command);
		}
		
		public synchronized Command commandInQueue() {
			if (cmdQueue.size() == 0) return null;
			
			Command c = cmdQueue.remove(0);
			return c;
		}
		
		public synchronized int getCommandsInQueueCount() {
			return cmdQueue.size();
		}
		
		private synchronized boolean sendAndWait(Command c, int timeout) {
			if (c == null) return true;
			
			waitingCommandID = c.id;
			
			keepWaiting = true;
			replied = false;
			
			/*if (! keepRunning) return false;*/ 
				
			sendCommand(c);
			
			int toWait = timeout;
			long waitStart = System.currentTimeMillis();

			while (/*keepRunning &&*/ keepWaiting) {
				try {
					wait(toWait);
					keepWaiting = false; 
				} catch (InterruptedException e) {
					toWait -= waitStart - System.currentTimeMillis();
					waitStart = System.currentTimeMillis();
					if (toWait <= 0) keepWaiting = false;
				}				
			}
			
			if (! replied) {
				keepRunning = false;
				log.warning("Hardware didn't reply. Failed!");
				escFailed(c, true);
			} else if (! ack) {
				log.warning("Hardware didn't ACK. Failed");
				escFailed(c, false);
			}
			
			waitingCommandID = CMD_NONE;
			return replied;
		}
		
		
		@Override
		public void run() {
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			
			log.finer("Sending HELLO (" + CLLCommProtocol.CMD_HELLO + ")");
			if (!sendAndWait(new Command(CLLCommProtocol.CMD_HELLO, 0), START_TIMEOUT)) return;

			log.finer("Sending SET_NESC (" + CLLCommProtocol.CMD_SET_NESC + ") " + escs.size());
			if (!sendAndWait(new Command(CLLCommProtocol.CMD_SET_NESC, escs.size()), START_TIMEOUT)) return;

			log.finer("Sending SET_TMIN (" + CLLCommProtocol.CMD_SET_TMIN + ") " + throttleMin);
			if (! sendAndWait(new Command(CLLCommProtocol.CMD_SET_TMIN, throttleMin), START_TIMEOUT)) return;

			log.finer("Sending SET_TMAX (" + CLLCommProtocol.CMD_SET_TMAX + ") " + throttleMax);
			if (! sendAndWait(new Command(CLLCommProtocol.CMD_SET_TMAX, throttleMax), START_TIMEOUT)) return;

			log.finer("Sending SET_TMODE (" + CLLCommProtocol.CMD_SET_TMODE + ") " + throttleMode);
			if (! sendAndWait(new Command(CLLCommProtocol.CMD_SET_TMODE, throttleMode), START_TIMEOUT)) return;

			log.finer("Sending START (" + CLLCommProtocol.CMD_START + ") " );
			if (! sendAndWait(new Command(CLLCommProtocol.CMD_START, 0), START_TIMEOUT)) return;
			
			startCompleted();
			
			while (isRunning() || (getCommandsInQueueCount() > 0) ) {
				if (!sendAndWait(commandInQueue(), RUN_TIMEOUT)) break;
				
				if (isArmed() && throttleMode == SOFTWARE_THROTTLE) {
					if (!sendAndWait(new Command(CLLCommProtocol.CMD_SET_THROTTLE, throttle), RUN_TIMEOUT)) break;
				} else {
					if (!sendAndWait(new Command(CLLCommProtocol.CMD_NOOP, 0), RUN_TIMEOUT)) break;
				}
				
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					log.finest("ESCInterface thread interrupted!");
				}

			}

			stopCompleted();
		}	
	}

	public static Logger log = null;
	private static final int LOGLEVEL = Logger.FINEST;
	private static final String LOGNAME = "it.picciux.castle.linklive";
	
	/**
	 * Library version
	 */
	public static final String VERSION = "0.1.0rc1";
	
	/**
	 * Library progressive version number
	 */
	public static final int VERSION_NUMBER = 0;
	
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
	
	/**
	 * Default value for throttle pulse length corresponding to
	 * idle/break (in microseconds)
	 */
	public static final int DEFAULT_THROTTLE_MIN = 1000; //micro seconds
	
	/**
	 * Default value for throttle pulse length corresponding to 
	 * full throttle (in microseconds)
	 */
	public static final int DEFAULT_THROTTLE_MAX = 2000; //micro seconds

	/**
	 * Absolute minimum pulse length for idle throttle
	 */
	public static final int ABSOLUTE_THROTTLE_MIN = 750; //micro seconds
	
	/**
	 * Absolute maximum pulse length for full throttle
	 */
	public static final int ABSOLUTE_THROTTLE_MAX = 2250; //micro seconds
	
	/**
	 * Vector holding {@link CastleESC} objects to store and return data
	 */
	private Vector<CastleESC> escs = new Vector<CastleESC>();
	
	/**
	 * Protocol data parser
	 */
	private CLLCommProtocol parser = new CLLCommProtocol();
	
	/**
	 * Event-handler to be set by user
	 */
	private ICastleLinkLiveEvent eventHandler = null;
	
	/**
	 * Throttle mode (defaults to software generated throttle signal)
	 */
	private int throttleMode = SOFTWARE_THROTTLE;
	
	/**
	 * Throttle value to be sent to ESC interface
	 */
	private int throttle = 50;
	
	/**
	 * throttle pulse length for idle/brake
	 */
	private int throttleMin = DEFAULT_THROTTLE_MIN;
	
	/**
	 * throttle pulse lenght for full throttle
	 */
	private int throttleMax = DEFAULT_THROTTLE_MAX;
	
	/**
	 * Connection status to ESC interface
	 */
	private boolean connected = false;
	
	/**
	 * Whether the ESC interface is armed or not
	 */
	private boolean armed = false;
	
	/**
	 * Throttle state (present/valid vs not-present/invalid) as
	 * reported by the ESC interface
	 */
	private boolean throttlePresent = false;
	
	/**
	 * reference to the running ESCInterfaceThread
	 */
	private ESCInterfaceThread escInterfaceThread;
	
	/**
	 * The output stream to use when sending data to the ESC interface
	 */
	private OutputStream outStream;
	
	/**
	 * Sends a {@link Command} to the ESC interface
	 * @param command
	 */
	private void sendCommand(Command command) {
		if (command == null) return;

		log.finest("sendingCommand " + command.id + ": " + command.value);
		
		int [] buf = new int[] {
				0x00, 0x00, 0x00
		};
		
		int checksum = CLLCommProtocol.OUT_HEADER;
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
	
	/**
	 * Called by {@link ESCInterfaceThread} when ESC interface
	 * didn't reply or NACKed our command
	 * @param command the failed command
	 * @param timeout whether was a timeout or NACKed condition
	 */
	private void escFailed(Command command, boolean timeout) {
		escInterfaceThread = null;
		String reason = "";
		
		if (! timeout) {
			switch(command.id) {
				case CLLCommProtocol.CMD_HELLO:
					reason = "ESC interface didn't respond to our handshake";
					break;
				case CLLCommProtocol.CMD_SET_NESC:
					reason = "Cannot set n.ESC to " + command.value; 
					break;
				case CLLCommProtocol.CMD_SET_TMAX:
					reason = "Cannot set throttle maximum to " + command.value; 
					break;
				case CLLCommProtocol.CMD_SET_TMIN:
					reason = "Cannot set throttle minimum to " + command.value; 
					break;
				case CLLCommProtocol.CMD_SET_TMODE:
					reason = "Cannot set throttle mode to " + command.value; 
					break;
				case CLLCommProtocol.CMD_START:
					reason = "ESC interface didn't start";
					break;
			}
		} else {
			reason = "ESC interface didn't respond to our ";
			switch(command.id) {
			case CLLCommProtocol.CMD_HELLO:
				reason += "handshake";
				break;
			case CLLCommProtocol.CMD_SET_NESC:
				reason += "set n. ESC"; 
				break;
			case CLLCommProtocol.CMD_SET_TMAX:
				reason += "set throttle maximum"; 
				break;
			case CLLCommProtocol.CMD_SET_TMIN:
				reason += "set throttle minimum"; 
				break;
			case CLLCommProtocol.CMD_SET_TMODE:
				reason += "set throttle mode"; 
				break;
			case CLLCommProtocol.CMD_START:
				reason += "start command"; 
				break;
			case CLLCommProtocol.CMD_SET_THROTTLE:
				reason += "set throttle command";
				break;
			case CLLCommProtocol.CMD_ARM:
				reason += "arm command";
				break;
			case CLLCommProtocol.CMD_DISARM:
				reason += "disarm command";
				break;
			case CLLCommProtocol.CMD_NOOP:
				reason += "noop command";
				break;
			}
		}
		
		if (eventHandler != null) eventHandler.connectionError(reason);
	}
	
	/**
	 * Called by {@link ESCInterfaceThread} when it completed
	 * connection procedure to ESC interface
	 */
	private void startCompleted() {
		setConnected(true);
		if (eventHandler != null) eventHandler.connectionEvent(true);
	}

	/**
	 * Called by {@link ESCInterfaceThread} at thread code termination
	 */
	private void stopCompleted() {
		setConnected(false);
		if (eventHandler != null) eventHandler.connectionEvent(false);
		escInterfaceThread = null;
	}
	
	/**
	 * Class constructor
	 */
	public CastleLinkLive() {
		try {
			log = Logger.getLogger(LOGNAME, Logger.CONSOLE, null);
		} catch (LoggerException e) {
			log = Logger.getNullLogger(LOGNAME);
		}
		
		log.setLevel(LOGLEVEL);
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
					if ( (escInterfaceThread != null) ) {
						if (parser.getResponse() == CLLCommProtocol.RESPONSE_ACK)
							escInterfaceThread.ack();
						else
							escInterfaceThread.nack();
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
	 * (as set by {@link CastleLinkLive#start(int, int)} method)
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
	 * @return throttle pulse duration corresponding to min throttle (in microseconds)
	 */
	public int getThrottleMin() {
		return throttleMin;
	}

	/**
	 * Sets pulse-duration for idle throttle (in microseconds).
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
	 * Sets pulse-duration for full throttle (in microseconds).
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
	 * @return whether ESC interface is armed
	 * @see CastleLinkLive#arm()
	 */
	public synchronized boolean isArmed() {
		return armed;
	}

	/**
	 * internal function to thread-safely set armed state
	 * @param armed
	 */
	private synchronized void setArmed(boolean armed) {
		this.armed = armed;
	}
	
	/**
	 * Enables the ESC interface to start generating/managing throttle signal,
	 * whether it is software generated or external
	 */
	public void arm() {
		if (isArmed()) return;
		
		throttle = 50;
		//armed = true;
		
		escInterfaceThread.postCommand(new Command(CLLCommProtocol.CMD_ARM, 0));
	}

	/**
	 * Asks the ESC interface to stop generating/managing throttle signal,
	 * whether it is software generated or external
	 */
	public void disarm() {
		if (! isArmed()) return;
		
		//armed = false;
		
		if (escInterfaceThread != null)
			escInterfaceThread.postCommand(new Command(CLLCommProtocol.CMD_DISARM, 0));
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
	 * @throws InvalidArgumentException if any of the arguments is not valid or out of bounds
	 */
	public boolean start(int throttleMode, int nESC) throws InvalidArgumentException {
		if ( (throttleMode != SOFTWARE_THROTTLE) && (throttleMode != EXTERNAL_THROTTLE) )
			throw new InvalidArgumentException(throttleMode + " is not a valid throttleMode");

		this.throttleMode = throttleMode;
		
		if (nESC < 1 || nESC > 2) 
			throw new InvalidArgumentException("We support 1 or 2 ESC");
		
		escs.clear();
		for (int i = 0; i < nESC; i++)
			escs.add(new CastleESC());
		
		escInterfaceThread = new ESCInterfaceThread();
		escInterfaceThread.start();		
		
		return true;
	}
	
	/**
	 * Cancels a session start attempt.
	 */
	public void cancelStart() {
		if (escInterfaceThread != null && escInterfaceThread.isRunning() && (! isConnected())) 
			escInterfaceThread.cancel();
	}
		
	/**
	 * Terminates an already established session with ESC interface (it's
	 * program responsibility to close underlying streams for communication
	 * with ESC interface)
	 */
	public void stop() {
		if (escInterfaceThread != null && escInterfaceThread.isRunning()) {
			if (isArmed()) 
				escInterfaceThread.postCommand(new Command(CLLCommProtocol.CMD_DISARM, 0));

			//ESCInterfaceThread will set armed status when DISARM ACKed
			//setArmed(false);
			
			escInterfaceThread.terminate();
			throttlePresent = false;
		}
		
		//throttle thread signals disconnection at thread termination calling stopCompleted
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

	/**
	 * @return whether CastleLinkLive is connected to the ESC interface
	 */
	public synchronized boolean isConnected() {
		return connected;
	}
	
	/**
	 * internal function to set thread-safely set connected state
	 * @param connected
	 */
	private synchronized void setConnected(boolean connected) {
		this.connected = connected;
	}
}
