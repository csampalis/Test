package com.zebraprint.epsilonnet;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.Set;


public class ZebraBluetoothPrinter extends CordovaPlugin {

    private static final String LOG_TAG = "ZebraBluetoothPrinter";
    private CallbackContext callbackContext2;
    private boolean printerFound;
    private Connection thePrinterConn2;
    private PrinterStatus printerStatus2;
    private ZebraPrinter printer2;
    private final int MAX_PRINT_RETRIES = 1;
    private int speed;
    private int time;
    private int number;
    //String mac = "AC:3F:A4:1D:7A:5C";

    public ZebraBluetoothPrinter() {
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		this.callbackContext2 = callbackContext;
		if (action.equals("printImage")) {
            try {
                String labels = args.getString(1);
                String MACAddress = args.getString(0);
                speed = args.getInt(2);
                time = args.getInt(3);
                number = args.getInt(4);
                // for (int i = 1; i < number; i++)
                // {
                    // labels.put(labels.get(0));
                // }
                sendImage(labels, MACAddress);
            } catch (IOException e) {
               callbackContext.error(e.getMessage());
            }
            return true;
        }
        if (action.equals("print")) {
            try {
                String mac = args.getString(0);
                String msg = args.getString(1);

				// cordova.getActivity().runOnUiThread(new Runnable() {
					// public void run() {
						// Toast toast = Toast.makeText(cordova.getActivity().getApplicationContext(), "1", Toast.LENGTH_SHORT);
						// toast.show();
					// }
				// });

                sendData(callbackContext, mac, msg);
            } catch (Exception e) {
                //Log.e(LOG_TAG, e.getMessage());
                //e.printStackTrace();
				callbackContext.error(e.getMessage());
            }
            return true;
        }
        if (action.equals("find")) {
            try {
                findPrinter(callbackContext);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }
    
    public void findPrinter(final CallbackContext callbackContext) {
      try {
          BluetoothDiscoverer.findPrinters(this.cordova.getActivity().getApplicationContext(), new DiscoveryHandler() {

              public void foundPrinter(DiscoveredPrinter printer) {
                  String macAddress = printer.address;
                  //I found a printer! I can use the properties of a Discovered printer (address) to make a Bluetooth Connection
                  callbackContext.success(macAddress);
              }

              public void discoveryFinished() {
                  //Discovery is done
              }

              public void discoveryError(String message) {
                  //Error during discovery
                  callbackContext.error(message);
              }
          });
      } catch (Exception e) {
          e.printStackTrace();
      }      
    }

	 private void sendImage(final String labels, final String MACAddress) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                printLabels(labels, MACAddress);
            }
        }).start();
    }
	
	 private void printLabels(String labels, String MACAddress) {
        try {

            boolean isConnected = openBluetoothConnection(MACAddress);

            if (isConnected) {
                initializePrinter();

                boolean isPrinterReady = getPrinterStatus(0);

                if (isPrinterReady) {

                    printLabel(labels);

                    //Voldoende wachten zodat label afgeprint is voordat we een nieuwe printer-operatie starten.

                    //Thread.sleep(15000);
					
					//SGD.SET("device.languages", "line_print", thePrinterConn);

                    thePrinterConn2.close();

                    callbackContext2.success();
                } else {
                    Log.e(LOG_TAG, "Printer not ready");
                    callbackContext2.error("Tiskalnik še ni pripravljen.");
                }

            }

        } catch (ConnectionException e) {
            Log.e(LOG_TAG, "Connection exception: " + e.getMessage());

            //De connectie tussen de printer & het toestel is verloren gegaan.
            if (e.getMessage().toLowerCase().contains("broken pipe")) {
                callbackContext2.error("Povezava med napravo in tiskalnikom je bila prekinjena. Poskusite znova.");

                //Geen printer gevonden via bluetooth, -1 teruggeven zodat er gezocht wordt naar nieuwe printers.
            } else if (e.getMessage().toLowerCase().contains("socket might closed")) {
                int SEARCH_NEW_PRINTERS = -1;
                callbackContext2.error(SEARCH_NEW_PRINTERS);
            } else {
                callbackContext2.error("Prišlo je do neznane napake tiskalnika. Znova zaženite tiskalnik in poskusite znova.");
            }

        } catch (ZebraPrinterLanguageUnknownException e) {
            Log.e(LOG_TAG, "ZebraPrinterLanguageUnknown exception: " + e.getMessage());
            callbackContext2.error("Prišlo je do neznane napake tiskalnika. Znova zaženite tiskalnik in poskusite znova.");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception: " + e.getMessage());
            callbackContext2.error(e.getMessage());
        }
    }
	
	private void printLabel(String labels) throws Exception {
        ZebraPrinterLinkOs zebraPrinterLinkOs = ZebraPrinterFactory.createLinkOsPrinter(printer2);

       
            String base64Image = labels;
            byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);

            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            ZebraImageAndroid zebraimage = new ZebraImageAndroid(decodedByte);

int labelHeight = Integer.valueOf(zebraimage.getHeight());
int labelSleep = (Integer.valueOf(labelHeight / 400) * 1000) * speed;


            //Lengte van het label eerst instellen om te kleine of te grote afdruk te voorkomen
            if (zebraPrinterLinkOs != null) {
                setLabelLength(zebraimage);
            }

            if (zebraPrinterLinkOs != null) {
                printer2.printImage(zebraimage, 20, 20, zebraimage.getWidth(), zebraimage.getHeight(), false);
            } else {
                Log.d(LOG_TAG, "Storing label on printer...");
                printer2.storeImage("wgkimage.pcx", zebraimage, -1, -1);
                printImageTheOldWay(zebraimage);
                SGD.SET("device.languages", "line_print", thePrinterConn2);
            }

            Thread.sleep(labelSleep);
          
                Thread.sleep(1000 * time);
           
        

    }
	
	 private boolean getPrinterStatus(int retryAttempt) throws Exception {
        try {
            printerStatus2 = printer2.getCurrentStatus();

            if (printerStatus2.isReadyToPrint) {
                Log.d(LOG_TAG, "Printer is ready to print...");
                return true;
            } else {
                if (printerStatus2.isPaused) {
                    throw new Exception("Printer is gepauzeerd. Gelieve deze eerst te activeren.");
                } else if (printerStatus2.isHeadOpen) {
                    throw new Exception("Printer staat open. Gelieve deze eerst te sluiten.");
                } else if (printerStatus2.isPaperOut) {
                    throw new Exception("Gelieve eerst de etiketten aan te vullen.");
                } else {
                    throw new Exception("Kon de printerstatus niet ophalen. Gelieve opnieuw te proberen. " +
                        "Herstart de printer indien dit probleem zich blijft voordoen");
                }
            }
        } catch (ConnectionException e) {
            if (retryAttempt < MAX_PRINT_RETRIES) {
                Thread.sleep(5000);
                return getPrinterStatus(++retryAttempt);
            } else {
                throw new Exception("Kon de printerstatus niet ophalen. Gelieve opnieuw te proberen. " +
                    "Herstart de printer indien dit probleem zich blijft voordoen.");
            }
        }

    }

    /**
     * Gebruik de Zebra Android SDK om de lengte te bepalen indien de printer LINK-OS ondersteunt
     *
     * @param zebraimage
     * @throws Exception
     */
    private void setLabelLength(ZebraImageAndroid zebraimage) throws Exception {
        ZebraPrinterLinkOs zebraPrinterLinkOs = ZebraPrinterFactory.createLinkOsPrinter(printer2);

        if (zebraPrinterLinkOs != null) {
            String currentLabelLength = zebraPrinterLinkOs.getSettingValue("zpl.label_length");
			Log.d(LOG_TAG, "mitja " + currentLabelLength);
            if (!currentLabelLength.equals(String.valueOf(zebraimage.getHeight()))) {
				// printer_diff
				Log.d(LOG_TAG, "mitja me " + zebraimage.getHeight());
                zebraPrinterLinkOs.setSetting("zpl.label_length", zebraimage.getHeight() + "");
            }
        }
    }
	
	private void initializePrinter() throws ConnectionException, ZebraPrinterLanguageUnknownException {
        Log.d(LOG_TAG, "Initializing printer...");
        printer2 = ZebraPrinterFactory.getInstance(thePrinterConn2);
        String printerLanguage = SGD.GET("device.languages", thePrinterConn2);

        if (!printerLanguage.contains("zpl")) {
			// print diff
            SGD.SET("device.languages", "hybrid_xml_zpl", thePrinterConn2);
            Log.d(LOG_TAG, "printer language set...");
        }
    }

	 private boolean openBluetoothConnection(String MACAddress) throws ConnectionException {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter.isEnabled()) {
            Log.d(LOG_TAG, "Creating a bluetooth-connection for mac-address " + MACAddress);

            thePrinterConn2 = new BluetoothConnection(MACAddress);

            Log.d(LOG_TAG, "Opening connection...");
            thePrinterConn2.open();
            Log.d(LOG_TAG, "connection successfully opened...");

            return true;
        } else {
            Log.d(LOG_TAG, "Bluetooth is disabled...");
            callbackContext2.error("Bluetooth ni vklopljen.");
        }

        return false;
    }
   

    private void printImageTheOldWay(ZebraImageAndroid zebraimage) throws Exception {

        Log.d(LOG_TAG, "Printing image...");

        String cpcl = "! 0 200 200 ";
        cpcl += zebraimage.getHeight();
        cpcl += " 1\r\n";
		// print diff
        cpcl += "PW 750\r\nTONE 0\r\nSPEED 6\r\nSETFF 203 5\r\nON - FEED FEED\r\nAUTO - PACE\r\nJOURNAL\r\n";
		//cpcl += "TONE 0\r\nJOURNAL\r\n";
        cpcl += "PCX 150 0 !<wgkimage.pcx\r\n";
        cpcl += "FORM\r\n";
        cpcl += "PRINT\r\n";
        thePrinterConn2.write(cpcl.getBytes());

    }
   /*
     * This will send data to be printed by the bluetooth printer
     */
    void sendData(final CallbackContext callbackContext, final String mac, final String msg) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
				
					
                    Connection thePrinterConn = new BluetoothConnectionInsecure(mac);

					 if (isPrinterReady(thePrinterConn)) {

                        // Open the connection - physical connection is established here.
                        thePrinterConn.open();

                        // Send the data to printer as a byte array.
//                        thePrinterConn.write("^XA^FO0,20^FD^FS^XZ".getBytes());
                        thePrinterConn.write(msg.getBytes());


                        // Make sure the data got to the printer before closing the connection
                        Thread.sleep(500);

                        // Close the insecure connection to release resources.
                        thePrinterConn.close();
                        callbackContext.success("Done");
                    } else {
						callbackContext.error("printer is not ready");
					}
                } catch (Exception e) {
                    // Handle communications error here.
                    callbackContext.error(e.getMessage());
                }
            }
        }).start();
    }

    private Boolean isPrinterReady(Connection connection) throws ConnectionException, ZebraPrinterLanguageUnknownException {
         Boolean isOK = false;
        connection.open();
        // Creates a ZebraPrinter object to use Zebra specific functionality like getCurrentStatus()
        ZebraPrinter printer = ZebraPrinterFactory.getInstance(connection);
        PrinterStatus printerStatus = printer.getCurrentStatus();
        if (printerStatus.isReadyToPrint) {
            isOK = true;
        } else if (printerStatus.isPaused) {
		connection.close();
            throw new ConnectionException("Cannot Print because the printer is paused.");
        } else if (printerStatus.isHeadOpen) {
		connection.close();
            throw new ConnectionException("Cannot Print because the printer media door is open.");
        } else if (printerStatus.isPaperOut) {
		connection.close();
            throw new ConnectionException("Cannot Print because the paper is out.");
        } else {
		connection.close();
            throw new ConnectionException("Cannot Print.");
        }
		 connection.close();
        return isOK;
    }
}

