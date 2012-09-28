/* SVN: $Id$ */

/*****************************************************************************
 *  CastleLinkLiveMonitor for windowed systems  - PreferencesWindow.java
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

package it.picciux.castle.linklive.win;


import it.picciux.castle.linklive.CastleLinkLive;
import it.picciux.castle.linklive.win.CastleLinkLiveMonitor.AppSettings;
import it.picciux.commlayer.CommLayer;
import it.picciux.commlayer.CommLayerPort;
import it.picciux.commlayer.ICommEventListener;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

public class PreferencesWindow {
	private class EventListener implements SelectionListener {
		@Override
		public void widgetDefaultSelected(SelectionEvent e) {
		}

		@Override
		public void widgetSelected(SelectionEvent e) {
			if (e.widget == tMinScale) {
				tMinSpin.setSelection(tMinScale.getSelection());
				tempSettings.throttleMin = tMinScale.getSelection();				
			} else if (e.widget == tMinSpin) {
				tMinScale.setSelection(tMinSpin.getSelection());
				tempSettings.throttleMin = tMinSpin.getSelection();
			} else if (e.widget == tMaxScale) {
				tMaxSpin.setSelection(tMaxScale.getSelection());
				tempSettings.throttleMax = tMaxScale.getSelection();
			} else if (e.widget == tMaxSpin) {
				tMaxScale.setSelection(tMaxSpin.getSelection());
				tempSettings.throttleMax = tMaxSpin.getSelection();
			} else if (e.widget == tModeSoftwareButton) {
				tempSettings.throttleMode = CastleLinkLive.SOFTWARE_THROTTLE;
				tModeSoftwareButton.setSelection(true);
				tModeExternalButton.setSelection(false);
			} else if (e.widget == tModeExternalButton) {
				tempSettings.throttleMode = CastleLinkLive.EXTERNAL_THROTTLE;
				tModeExternalButton.setSelection(true);
				tModeSoftwareButton.setSelection(false);
			} else if (e.widget == nEscSpinner) {
				tempSettings.nESC = nEscSpinner.getSelection();
			} else if (e.widget == motorPolesSpinner) {
				int p = motorPolesSpinner.getSelection();
				if ( p % 2 != 0 ) {
					p++;
					motorPolesSpinner.setSelection(p);
				}
				tempSettings.motorPoles = p;
			} else if (e.widget == portCombo) {
				tempSettings.port = portCombo.getItem(portCombo.getSelectionIndex());
			} else if (e.widget == portText) {
				tempSettings.port = portText.getText();
			} else if (e.widget == btnOK) {
				saveSettings();
				w.dispose();
			} else if (e.widget == btnCancel) {
				w.dispose();
			}

		}
	}
	
	private class ScanListener implements ICommEventListener {
		@Override
		public void connectionEvent(final int status, final Object extraData) {
			w.getDisplay().syncExec(new Runnable() {
				
				@Override
				public void run() {
					switch (status) {
					case CommLayer.PORTSCAN_STARTED:
						portCombo.removeAll();
						break;
					case CommLayer.PORTSCAN_CANCELED:
						portCombo.setEnabled(true);
						break;
					case CommLayer.PORTSCAN_ERROR:
						//TODO error signaling
						break;
					case CommLayer.PORTSCAN_COMPLETED:
						for (int i = 0; i < portCombo.getItemCount(); i++) {
							if (portCombo.getItem(i).equalsIgnoreCase(settings.port)) {
								portCombo.select(i);
								break;
							}
						}
						portCombo.setEnabled(true);
						break;
					case CommLayer.PORTSCAN_PORT_FOUND:
						portCombo.add(((CommLayerPort) extraData).getName());
						break;
					}
				}
			});
		}
	}
	
	private AppSettings settings;
	private AppSettings tempSettings;
	private CommLayer layer;
	
	private Scale tMinScale;
	private Spinner tMinSpin;
	private Scale tMaxScale;
	private Spinner tMaxSpin;
	
	private Spinner nEscSpinner;
	
	private Spinner motorPolesSpinner;
	
	private Combo portCombo;
	private Text portText;
	
	private Button tModeSoftwareButton;
	private Button tModeExternalButton;
	
	private Shell w;
	
	private Button btnOK;
	private Button btnCancel;
	
	private EventListener listener = new EventListener();
	private ScanListener scanListener = new ScanListener();
	
	public PreferencesWindow(Shell parent, AppSettings settings, CommLayer layer) {
		w = new Shell(parent, SWT.DIALOG_TRIM);
		this.settings = settings;
		try {
			this.tempSettings = this.settings.clone();
		} catch (CloneNotSupportedException e) {
			System.out.println("Clone not supported");
		}
		this.layer = layer;
		init();
	}


	private void init() {
		w.setLayout(new RowLayout(SWT.VERTICAL));

		int tCenter = CastleLinkLive.ABSOLUTE_THROTTLE_MIN +  
				((CastleLinkLive.ABSOLUTE_THROTTLE_MAX - CastleLinkLive.ABSOLUTE_THROTTLE_MIN) / 2);
 
		System.out.println("tCenter: " + tCenter);
		
		Group controls = new Group(w, SWT.NONE);
		controls.setLayout(new RowLayout(SWT.HORIZONTAL));
		
		Group buttons = new Group(w, SWT.NONE);
		buttons.setLayout(new RowLayout(SWT.HORIZONTAL));
		
		Group throttleGroup = new Group(controls, SWT.NONE);
		throttleGroup.setLayout(new RowLayout(SWT.VERTICAL));
		throttleGroup.setText("Throttle");
		
		Group tLimitsGroup = new Group(throttleGroup, SWT.NONE);
		tLimitsGroup.setLayout(new RowLayout(SWT.HORIZONTAL));
		
		Group tMinGroup = new Group(tLimitsGroup, SWT.NONE);
		tMinGroup.setLayout(new RowLayout(SWT.VERTICAL));
		tMinGroup.setText("Min (us)");
		tMinScale = new Scale(tMinGroup, SWT.VERTICAL);
		tMinScale.setMaximum(tCenter -1);
		tMinScale.setMinimum(CastleLinkLive.ABSOLUTE_THROTTLE_MIN);
		tMinScale.setSelection(this.settings.throttleMin);	
		tMinSpin = new Spinner(tMinGroup, SWT.BORDER);
		tMinSpin.setMaximum(tMinScale.getMaximum());
		tMinSpin.setMinimum(tMinScale.getMinimum());
		tMinSpin.setSelection(this.settings.throttleMin);

		tMinScale.addSelectionListener(listener);
		tMinSpin.addSelectionListener(listener);
		
		
		Group tMaxGroup = new Group(tLimitsGroup, SWT.NONE);
		tMaxGroup.setLayout(new RowLayout(SWT.VERTICAL));
		tMaxGroup.setText("Max (us)");
		tMaxScale = new Scale(tMaxGroup, SWT.VERTICAL);
		tMaxScale.setMaximum(CastleLinkLive.ABSOLUTE_THROTTLE_MAX);
		tMaxScale.setMinimum(tCenter +1);
		tMaxScale.setSelection(this.settings.throttleMax);
		tMaxSpin = new Spinner(tMaxGroup, SWT.BORDER);
		tMaxSpin.setMaximum(tMaxScale.getMaximum());
		tMaxSpin.setMinimum(tMaxScale.getMinimum());
		tMaxSpin.setSelection(this.settings.throttleMax);
		
		tMaxScale.addSelectionListener(listener);
		tMaxSpin.addSelectionListener(listener);

		Group tModeGroup = new Group(throttleGroup, SWT.NONE);
		tModeGroup.setText("Mode");
		tModeGroup.setLayout(new RowLayout(SWT.HORIZONTAL));
		tModeSoftwareButton = new Button(tModeGroup, SWT.RADIO);
		tModeSoftwareButton.setText("Software");
		tModeSoftwareButton.setSelection((settings.throttleMode == CastleLinkLive.SOFTWARE_THROTTLE));
		
		tModeExternalButton = new Button(tModeGroup, SWT.RADIO);
		tModeExternalButton.setText("External");
		tModeExternalButton.setSelection(! (settings.throttleMode == CastleLinkLive.SOFTWARE_THROTTLE));
	
		tModeSoftwareButton.addSelectionListener(listener);
		tModeExternalButton.addSelectionListener(listener);
		
		Group escGroup = new Group(controls, SWT.NONE);
		escGroup.setLayout(new RowLayout(SWT.VERTICAL));
		
		Label l = new Label(escGroup, SWT.NONE);
		l.setText("ESCs connected");
		nEscSpinner = new Spinner(escGroup, SWT.BORDER);
		nEscSpinner.setMaximum(2);
		nEscSpinner.setMinimum(1);
		nEscSpinner.setSelection(settings.nESC);
		nEscSpinner.addSelectionListener(listener);
		
		l = new Label(escGroup, SWT.NONE);
		l.setText("Motor Poles Number");
		motorPolesSpinner = new Spinner(escGroup, SWT.BORDER);
		motorPolesSpinner.setMinimum(2);
		motorPolesSpinner.setMaximum(30);
		motorPolesSpinner.setIncrement(2);
		motorPolesSpinner.setPageIncrement(4);
		motorPolesSpinner.setSelection(settings.motorPoles);
		motorPolesSpinner.addSelectionListener(listener);
		
		Group comGroup = new Group(controls, SWT.NONE);
		comGroup.setLayout(new RowLayout(SWT.VERTICAL));
		comGroup.setText("Communication");
		
		l = new Label(comGroup, SWT.NONE);
		l.setText("COM Port");
		
		if (layer.canScan()) {
			portCombo = new Combo(comGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
			portCombo.setEnabled(false);
			portCombo.addSelectionListener(listener);
		} else {
			portText = new Text(comGroup, SWT.BORDER);
			portText.addSelectionListener(listener);
		}
		
		btnOK = new Button(buttons, SWT.PUSH);
		btnOK.setText("OK");
		btnOK.addSelectionListener(listener);
		
		btnCancel = new Button(buttons, SWT.PUSH);
		btnCancel.setText("Cancel");
		btnCancel.addSelectionListener(listener);
	}
	
	private void saveSettings() {
		settings.port = tempSettings.port;
		settings.nESC = tempSettings.nESC;
		settings.throttleMin = tempSettings.throttleMin;
		settings.throttleMax = tempSettings.throttleMax;
		settings.throttleMode = tempSettings.throttleMode;
		settings.motorPoles = tempSettings.motorPoles;
	}
	
	public void start() {
		Display display = w.getDisplay();
		w.pack();
		w.open();
		
		if (layer.canScan()) {
			layer.addEventListener(scanListener);
			layer.scan();
		}
		
		while(! w.isDisposed())
			if (! display.readAndDispatch())
				display.sleep();
		
		if (layer.canScan())
			layer.removeEventListener(scanListener);
	}
}
