/*****************************************************************************
 *  CastleLinkLiveMonitor for windowed systems - CastleLinkLiveMonitor.java
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

package it.picciux.castle.linklive.win;

import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;

import it.picciux.castle.linklive.CastleESC;
import it.picciux.castle.linklive.CastleLinkLive;
import it.picciux.castle.linklive.ICastleLinkLiveEvent;
import it.picciux.castle.linklive.InvalidDataException;
import it.picciux.castle.linklive.InvalidThrottleLimitException;
import it.picciux.commlayer.CommLayer;
import it.picciux.commlayer.CommLayerException;
import it.picciux.commlayer.DataReader;
import it.picciux.commlayer.ICommEventListener;
import it.picciux.commlayer.win.serial.SerialLayer;

public class CastleLinkLiveMonitor {
	private static class Reader extends DataReader {
		private CastleLinkLive cll;
		
		public Reader(CastleLinkLive cll) {
			super();
			this.cll = cll;
		}
		
		@Override
		protected void delay() {
		}

		@Override
		protected boolean readData() {
			//int data;
			byte[] data = new byte[40];
			int n = 0;
			
			try {
				n = in.read(data);
			} catch (IOException e) {
				return false;
			}
			
			if (n == -1) {
				disconnectCause = CommLayer.EOF;
				return false;
			}
			
			if (n > 0) {
				for (int i = 0; i < n; i++) {
					try {
						cll.putData(((int) data[i]) & 0xFF);
					} catch (InvalidDataException e) {
						dataErrors++;
						updateDataErrors();
					}
				}
				
				if (dataLoggers.size() > 0) {
					for (int i = 0; i < dataLoggers.size(); i++) {
						((DataLogger) dataLoggers.get(i)).writeData(data, 0, n);
					}
				}
			} 
			
			return true;
		}
		
	}

	
	public static class AppSettings implements Cloneable {
		public String port;
		int throttleMode = CastleLinkLive.SOFTWARE_THROTTLE;
		int nESC = 1;
		int throttleMin = CastleLinkLive.DEFAULT_THROTTLE_MIN;
		int throttleMax = CastleLinkLive.DEFAULT_THROTTLE_MAX;
		int motorPoles = 2;
		int logType = LOG_NONE;
		String logPath = "";

		@Override
		protected AppSettings clone() throws CloneNotSupportedException {
			AppSettings ret = (AppSettings) super.clone();
			ret.port = new String(port);
			ret.logPath = new String(logPath);
			return ret;
		}
	}
	
	//UI elements
	private static Display display;
	private static Shell mainWin;
	
	private static Label throttlePercent;
	private static Scale throttleScale;
	private static Label throttleValue;
	
	private static ProgressBar outputPower;
	private static Label outputPowerPercent;
	
	private static Label voltage;
	private static Label current;
	private static Label power;
	private static Label rpm;
	private static Label rippleVoltage;
	private static Label becVoltage;
	private static Label becCurrent;
	
	private static ProgressBar temperature;
	private static Label temperatureValue;
	
	private static Label throttlePresence;
	private static Label throttleModeDisplay;
	private static Label physicalLayerConnection;
	private static Label cllConnection;
	private static Label dataErrorsLabel;
	
	private static Button connectButton;
	private static Button armButton;
	private static Button setupButton;
	
	private static final String VOLTAGE_TITLE = "Voltage: ";
	private static final String CURRENT_TITLE = "Current: ";
	private static final String POWER_TITLE = "Power: ";
	private static final String RPM_TITLE = "RPM: ";
	private static final String RIPPLE_TITLE = "Ripple: ";
	private static final String BEC_VOLTAGE_TITLE = "BEC Voltage: ";
	private static final String BEC_CURRENT_TITLE = "BEC Current: ";
	
	private static final String THROTTLE_MODE_TITLE = "Throttle mode: ";
	
	private static final String DATA_ERRORS_TITLE = "Data errors: ";
	
	private static final int[] THROTTLE_PRESENT_COLOR = new int[] { 0, 255, 0 };
	private static final int[] THROTTLE_NOT_PRESENT_COLOR = new int[] { 255, 0, 0 };
	
	public static final int LOG_NONE = 0;
	public static final int LOG_RAW = 1;
	public static final int LOG_HR = 2;
	
	private static Color OKColor;
	private static Color KOColor;
	
	
	//Connection and communication elements
	private static AppSettings appSettings = new AppSettings();
	private static DataReader reader;
	private static SerialLayer layer;
	private static CastleLinkLive cll;
	private static DataLogger dataLogger = null;
	private static int dataErrors = 0;

	
	public CastleLinkLiveMonitor() {
	}

	private static void updateDataErrors() {
		display.syncExec(new Runnable() {
			
			@Override
			public void run() {
				dataErrorsLabel.setText(DATA_ERRORS_TITLE + dataErrors);
			}
		});
	}
	
	private static String throttleModeDescr(int throttleMode) {
		if (throttleMode == CastleLinkLive.EXTERNAL_THROTTLE)
			return "External";
		else if (throttleMode == CastleLinkLive.SOFTWARE_THROTTLE)
			return "Software";
		else
			return "?";
	}
	
	private static Group newControlsGroup(Composite win, String title, Layout layout) {
		Group g = new Group(win, SWT.SHADOW_NONE);
		g.setText(title);
		g.setLayout(layout);
		return g;
	}
	
	private static Group newControlsGroup(Composite win, String title, int rowLayoutStyle) {
		return newControlsGroup(win, title, new RowLayout(rowLayoutStyle));
	}
	
	private static Group newControlsGroup(Composite win, String title) {
		return newControlsGroup(win, title, SWT.VERTICAL);
	}
	
	private static Label newLabel(Composite parent, String text, int style) {
		Label l = new Label(parent, style);
		l.setText(text);
		return l;
	}
	
	private static Label newLabel(Composite parent, String text) {
		return newLabel(parent, text, SWT.NONE);
	}
	
	private static void throttleScaleSetValue(int value) {
		throttleScale.setSelection(throttleScale.getMaximum() - value);
	}
	
	private static int throttleScaleGetValue() {
		return (throttleScale.getMaximum() - throttleScale.getSelection());
	}
	
	public static double round(double v, int d) {
		double m = Math.pow(10.0d, d);
		return Math.round(v * m) / m;
	}
	
	private static void makeUI(Shell w) {
		w.setLayout(new RowLayout(SWT.VERTICAL));
		
		Group dataDisplayGroup = newControlsGroup(w, "");
		dataDisplayGroup.setLayout(new RowLayout(SWT.HORIZONTAL));
		
		Group statusGroup = newControlsGroup(w, "");
		statusGroup.setLayout(new RowLayout(SWT.HORIZONTAL));
		
		Group throttleGroup = newControlsGroup(dataDisplayGroup, "Throttle");
		throttlePercent = newLabel(throttleGroup, "050.0%");
		throttleScale = new Scale(throttleGroup, SWT.VERTICAL);
		throttleScale.setMinimum(0);
		throttleScale.setMaximum(100);
		throttleScaleSetValue(50);
		throttleValue = newLabel(throttleGroup, "1.500us");
		
		throttleScale.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				super.widgetSelected(e);
				if (cll.getThrottleMode() != CastleLinkLive.SOFTWARE_THROTTLE) return;
				
				int v = throttleScaleGetValue();
				cll.setThrottle(v);
				throttlePercent.setText(Integer.toString(v) + ".0%");
				throttleValue.setText(Double.toString(round((cll.getThrottleMin() + ((cll.getThrottleMax() - cll.getThrottleMin()) / 100.0d) * v) / 1000.0d, 3)));
			}
		});
		
		Group outputPowerGroup = newControlsGroup(dataDisplayGroup, "Output Power");
		outputPower = new ProgressBar(outputPowerGroup, SWT.VERTICAL);
		outputPower.setMinimum(0);
		outputPower.setMaximum(100);
		outputPower.setSelection(0);
		outputPowerPercent = newLabel(outputPowerGroup, "000.00%");
		
		Group dataGroup = newControlsGroup(dataDisplayGroup, "");
		voltage = newLabel(dataGroup, VOLTAGE_TITLE + "00.000V");
		current = newLabel(dataGroup, CURRENT_TITLE + "000.000A");
		power = newLabel(dataGroup, POWER_TITLE + "0000.0W");
		rpm = newLabel(dataGroup, RPM_TITLE + "00000");
		rippleVoltage = newLabel(dataGroup, RIPPLE_TITLE + "0.000V");
		becVoltage = newLabel(dataGroup, BEC_VOLTAGE_TITLE + "0.000V");
		becCurrent = newLabel(dataGroup, BEC_CURRENT_TITLE + "00.000A");
		dataErrorsLabel = newLabel(dataGroup, DATA_ERRORS_TITLE + "000");
		
		Group temperatureGroup = newControlsGroup(dataDisplayGroup, "Temperature");
		temperature = new ProgressBar(temperatureGroup, SWT.VERTICAL);
		temperature.setMinimum(-20);
		temperature.setMaximum(200);
		temperature.setSelection(19);
		
		temperatureValue = newLabel(temperatureGroup, "000.0°C");

		throttleModeDisplay = newLabel(statusGroup, THROTTLE_MODE_TITLE + throttleModeDescr(appSettings.throttleMode));
		
		physicalLayerConnection = newLabel(statusGroup, "COM");
		physicalLayerConnection.setBackground(KOColor);
		
		cllConnection = newLabel(statusGroup, "ESC");
		cllConnection.setBackground(KOColor);

		throttlePresence = newLabel(statusGroup, "Throttle");
		throttlePresence.setBackground(new Color(w.getDisplay(), 255, 0, 0));
		
		connectButton = new Button(statusGroup, SWT.PUSH);
		connectButton.setText("Disconnect");
		connectButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (! layer.isConnected()) {
					connect();
				} else {
					cll.stop();
					layer.disconnect();
				}
			}
		});
		
		armButton = new Button(statusGroup, SWT.TOGGLE);
		armButton.setText("Arm");
		armButton.setEnabled(false);
		armButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				super.widgetSelected(e);
				if (! armButton.getSelection())
					cll.disarm();
				else
					cll.arm();
			}
		});
		
		setupButton = new Button(statusGroup, SWT.PUSH);
		setupButton.setText("Setup");
		setupButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showPreferences();
			}
			
		});
		
		//final laying-out
		w.pack();
		
		connectButton.setText("Connect");
	}
	
	private static void showPreferences() {
		PreferencesWindow pref = new PreferencesWindow(mainWin, appSettings, layer);
		pref.start();
		
		throttleModeDisplay.setText(THROTTLE_MODE_TITLE + throttleModeDescr(appSettings.throttleMode));
		throttleScale.setEnabled(appSettings.throttleMode == CastleLinkLive.SOFTWARE_THROTTLE);
		
		System.out.println("Settings are:");
		System.out.println("n. ESC: " + appSettings.nESC);
		System.out.println("Motor poles: " + appSettings.motorPoles);
		System.out.println("throttle min: " + appSettings.throttleMin);
		System.out.println("throttle max: " + appSettings.throttleMax);
		System.out.println("throttle mode: " + appSettings.throttleMode);
		System.out.println("COM port: " + appSettings.port);
		System.out.println("LOG type: " + appSettings.logType);
		System.out.println("LOG path: " + appSettings.logPath);
	}
	
	private static void updateValues(CastleESC esc) {
		//long start = System.currentTimeMillis();
		mainWin.setRedraw(false);
		
		if (cll.getThrottleMode() == CastleLinkLive.EXTERNAL_THROTTLE) {
			double tp = round((((esc.getThrottle() * 1000.0d) - cll.getThrottleMin()) / (cll.getThrottleMax() - cll.getThrottleMin()) * 100.0d),1);
			throttlePercent.setText(tp  + "%");
			throttleValue.setText(Double.toString(round(esc.getThrottle(), 3)) + "us");
			throttleScaleSetValue((int) tp);
			
		}
		
		outputPower.setSelection(esc.getOutputPower());
		outputPowerPercent.setText(esc.getOutputPower() + "%");
		
		voltage.setText(VOLTAGE_TITLE + round(esc.getVoltage(), 3) + "V");
		current.setText(CURRENT_TITLE + round(esc.getCurrent(), 3) + "A");
		power.setText(POWER_TITLE + round(esc.getVoltage() + esc.getCurrent(), 1) + "W");
		rpm.setText(RPM_TITLE + esc.getRPM());
		rippleVoltage.setText(RIPPLE_TITLE + round(esc.getRippleVoltage(), 3) + "V");
		becVoltage.setText(BEC_VOLTAGE_TITLE + round(esc.getBECvoltage(), 3) + "V");
		becCurrent.setText(BEC_CURRENT_TITLE + round(esc.getBECcurrent(), 3) + "A");
		temperature.setSelection((int) esc.getTemperature());
		temperatureValue.setText(Double.toString(round(esc.getTemperature(), 1)) + "°C");
		
		mainWin.setRedraw(true);
		//System.out.println("UI update took " + (System.currentTimeMillis() - start) + " ms");
	}
	
	private static void uiThreadExec(Runnable runnable) {
		try {
			display.syncExec(runnable);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	private static void connect() {
		layer.getSettings().setPort(appSettings.port);

		dataErrors = 0;
		updateDataErrors();
		setupButton.setEnabled(false);
		connectButton.setEnabled(false);
		connectButton.setText("Connecting");
		
		layer.getSettings().setPort(appSettings.port);
		
		try {
			layer.connect();
		} catch (CommLayerException ex) {
			System.out.println(ex.getMessage());
			System.exit(-1);
		}
		
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		appSettings.port = "COM3";

		display = Display.getDefault();
		
		OKColor = new Color(
				display,
				THROTTLE_PRESENT_COLOR[0],
				THROTTLE_PRESENT_COLOR[1], 
				THROTTLE_PRESENT_COLOR[2]);
		
		KOColor = new Color(
				display,
				THROTTLE_NOT_PRESENT_COLOR[0],
				THROTTLE_NOT_PRESENT_COLOR[1], 
				THROTTLE_NOT_PRESENT_COLOR[2]);
		
		mainWin = new Shell(display, SWT.SHELL_TRIM);
		
		makeUI(mainWin);
		mainWin.open();
		
		cll = new CastleLinkLive();
		
		cll.setEventHandler(new ICastleLinkLiveEvent() {
			
			@Override
			public void throttlePresent(boolean present) {
				final Color c;
				
				if (present) {
					c = OKColor;
				} else {
					c = KOColor; 
				}
				
				uiThreadExec(new Runnable() {
					@Override
					public void run() {
						throttlePresence.setBackground(c);
					}
				});
			}
			
			@Override
			public void dataUpdated(final int index, final CastleESC esc) {
				//System.out.println("Updating " + esc.getVoltage());
				uiThreadExec(new Runnable() {
					@Override
					public void run() {
						updateValues(esc);
					}
				});
				
				if (appSettings.logType == LOG_HR && dataLogger != null) dataLogger.logESC(esc);
			}
			
			@Override
			public void connectionEvent(final boolean connected) {
				final Color c;
				String log;
				
				if (connected) {
					c = OKColor;
					log = "CastleLinkLive is connected!";
					if (appSettings.logType == LOG_HR && dataLogger != null)
						dataLogger.openLog();
				} else {
					c = KOColor;
					log = "CastleLinkLive is not connected";
					if (appSettings.logType == LOG_HR && dataLogger != null)
						dataLogger.closeLog();
				}
				
				uiThreadExec(new Runnable() {
					public void run() {
						armButton.setEnabled(connected);
						if (! connected) armButton.setSelection(false);
						cllConnection.setBackground(c);
					}
				});
				
				System.out.println(log);
			}

			@Override
			public void connectionError(final String reason) {
				uiThreadExec(new Runnable() {
					@Override
					public void run() {
						MessageBox mb = new MessageBox(mainWin, SWT.ICON_ERROR | SWT.OK);
						mb.setMessage(reason);
						mb.open();
					}
				});
				
				layer.disconnect();
			}
		});
		
		
		reader = new Reader(cll);
		layer = new SerialLayer(reader);

		SerialLayer.Settings settings = layer.getSettings();
		settings.setBaudRate(38400);
		settings.setDataBits(SerialLayer.Settings.DATABITS_8);
		settings.setParity(SerialLayer.Settings.PARITY_NONE);
		settings.setStopBits(SerialLayer.Settings.STOPBITS_1);
		settings.setFlowControl(SerialLayer.Settings.FLOWCONTROL_NONE);
		
		layer.addEventListener(new ICommEventListener() {
			
			@Override
			public void connectionEvent(int status, Object extraData) {
				
				switch(status) {
					case SerialLayer.CONNECTED:
						uiThreadExec(new Runnable() {
							@Override
							public void run() {
								physicalLayerConnection.setBackground(OKColor);
								connectButton.setText("Disconnect");
								connectButton.setEnabled(true);
							}
						});
						System.out.println("Serial Layer is connected");
						cll.setOutStream(layer.getOutputStream());
						try {
							cll.setThrottleMin(appSettings.throttleMin);
							cll.setThrottleMax(appSettings.throttleMax);
						} catch (InvalidThrottleLimitException e) {
							uiThreadExec(new Runnable() {
								@Override
								public void run() {
									MessageBox mb = new MessageBox(mainWin, SWT.ICON_ERROR | SWT.OK);
									mb.setMessage(
											appSettings.throttleMin + " and " + appSettings.throttleMax +
											" are not valid throttle limits."
									);
									mb.open();
								}
							});
						}
						
						if (appSettings.logType != LOG_NONE && appSettings.logPath.length() > 0) 
							dataLogger = new DataLogger(appSettings.logPath);
						
						if (appSettings.logType == LOG_RAW && dataLogger != null)
							reader.addDataLogger(dataLogger);
						
						cll.start(appSettings.throttleMode, appSettings.nESC);
						cll.getESC(0).setMotorPoles(appSettings.motorPoles);
						break;
						
					case SerialLayer.DISCONNECTED:
						System.out.println("Serial layer disconnected");
						cll.stop();
						
						if (appSettings.logType == LOG_RAW && dataLogger != null)
							reader.removeDataLogger(dataLogger);
						break;
						
					case SerialLayer.CONNECTION_STARTED:
						System.out.println("Connecting...");
						break;
					
					case SerialLayer.CONNECTION_BUSY:
						System.out.println("Port in use!");
						break;
					case SerialLayer.CONNECTION_ERROR:
						System.out.println("ERROR: " + ((String) extraData));
						break;
					case SerialLayer.CONNECTION_FAILED:
						System.out.println("Connection failed");
						break;
					case SerialLayer.NOSUCHDEV:
						System.out.println("Port doesn't exist");
						break;
				}

				if (status != SerialLayer.CONNECTED) {
					uiThreadExec(new Runnable() {
						@Override
						public void run() {
							physicalLayerConnection.setBackground(KOColor);
							throttlePresence.setBackground(KOColor);
							cllConnection.setBackground(KOColor);
							connectButton.setText("Connect");
							connectButton.setEnabled(true);
							armButton.setEnabled(false);
							armButton.setSelection(false);
							setupButton.setEnabled(true);
						}
					});
				}
				
			}
		});
		
		//UI loop
		while(! mainWin.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		
		cll.stop();
		layer.disconnect();
		//cmdThread.terminate();
		//System.exit(0);
	}

}
