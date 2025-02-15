package cs2114.aurem;

import android.graphics.Color;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.SeekBar;
import java.io.File;
import android.content.DialogInterface;
import android.widget.EditText;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationManager;
import android.view.View;
import android.os.IBinder;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

/**
 * // -------------------------------------------------------------------------
/**
 *  This is the Activity for the Aurem Equalizer.
 *
 *  @author Joseph O'Connor (jto2e);
 *  @author Laura Avakian (lavakian);
 *  @author Barbara Brown (brownba1);
 *  @version 2012.04.18
 */
public class AuremActivity extends Activity {


    private Intent intent;
    private EqualizerService eqService;

    private NotificationManager notificationManager;

    private EqualizerModel model;

    private Intent listIntent;

    private EqualizerView eqView;

    private boolean isServiceOn;

    private SeekBar[] seekBars;

    /**
     * Called when the activity is started.
     * @param savedInstanceState Bundle the saved state.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);


        intent = new Intent(this, EqualizerService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        isServiceOn = true;

        //All this stuff has to do with setting up a persisent
        //notification icon to let the user return to the app.

        PendingIntent contentIntent =
            PendingIntent.getActivity(this, 1, new Intent(this,
                AuremActivity.class), 0);
        notificationManager = (NotificationManager)
            getSystemService(Context.NOTIFICATION_SERVICE);
        int icon = R.drawable.ic_launcher;
        Notification notification = new Notification(icon,
            "AuremReloadedEQ", System.currentTimeMillis());
        Context context = getApplicationContext();
        CharSequence contentTitle = "AuremReloadedEQ";
        CharSequence contentText = getResources().getString(R.string.tapToReturn);
        notification.setLatestEventInfo(context, contentTitle,
            contentText, contentIntent);
        notification.flags |= Notification.FLAG_NO_CLEAR;
        notificationManager.notify(1, notification);

        model = new EqualizerModel(this);

        //Write the preset file if it doesn't exist already....
        File file = new File("/sdcard/AuremReloaded/presets.txt");
        if(!file.exists()) {
            model.writePresetFile();
        }
        model.readPresetFile();

        file = new File("/sdcard/AuremReloaded/lastState.txt");
        if(!file.exists()) {
            for(short i = 0; i < 5; i ++) {
                model.setBandLevel(i, (short) 0);
            }
        }
        else {
            model.readLastStateFile();
        }

        seekBars = new SeekBar[5];
        seekBars[0] = (SeekBar) findViewById(R.id.seekBar0);
        seekBars[1] = (SeekBar) findViewById(R.id.seekBar1);
        seekBars[2] = (SeekBar) findViewById(R.id.seekBar2);
        seekBars[3] = (SeekBar) findViewById(R.id.seekBar3);
        seekBars[4] = (SeekBar) findViewById(R.id.seekBar4);


        for(int i = 0; i < 5; i++) {
            seekBars[i].setMax(3000);
            seekBars[i].setProgress(model.getBandLevel((short) i) + 1500);
            seekBars[i].setOnSeekBarChangeListener(
                new SeekBarListener());
        }

        eqView = (EqualizerView) findViewById(R.id.equalizerView);
        eqView.setModel(model);
        eqView.setActivity(this);

        Button btnMore = (Button) findViewById(R.id.btnMore);
        btnMore.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                AlertDialog.Builder alert = new AlertDialog.Builder(AuremActivity.this);

                alert.setTitle("Credits");
                alert.setMessage(R.string.Credits);


                alert.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                });
                alert.show();
            }
        });
    }

    /**
     * Called when the application is resmued.
     */
    @Override
    public void onResume()
    {
        super.onResume();
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        isServiceOn = true;
    }

    /**
     * This is called when the test button is clicked.
     * @param view The view.
     */
    public void loadPresetClicked(View view) {
        String[] names = new String[10 + model.getPresets().size()];
        for(int i = 0; i < 10; i++) {
            names[i] = eqService.equalizer().getPresetName((short) i);
        }
        for (short i = 0; i < model.getPresets().size(); i++) {
            names[i + 10] = model.getPresets().get(i).getName();
        }

        this.onSaveInstanceState(new Bundle());

        listIntent = new Intent(this, PresetListView.class);
        //listIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        listIntent.putExtra("names", names);
        this.startActivityForResult(listIntent, 666);
    }

    /**
     * Called when the savePreset button is clicked.
     * @param view View the view being clicked.
     */
    public void savePresetClicked(View view)
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.newPreset);
        alert.setMessage(R.string.enterPreset);

        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
          String entered = input.getText().toString();
          short[] bands = new short[5];
          for(short i = 0; i < bands.length; i ++) {
              bands[i] = eqService.equalizer().getBandLevel(i);
          }
          model.createPreset(entered, bands);
          model.writePresetFile();
          }
        });

        alert.setNegativeButton(R.string.Cancel, new
            DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
            // Canceled.
          }
        });

        alert.show();
    }

    /**
     * Called when On/Off Button is clicked.
     * @param view View the view.
     */
    public void onOffClicked(View view)
    {
        if (isServiceOn == true) {
            notificationManager.cancelAll();
            eqService.equalizer().usePreset((short) 0);
            stopService(intent);
            isServiceOn = false;

            TextView txtAppTitle = (TextView) findViewById(R.id.txtAppTitle);
            LinearLayout upperLayout = (LinearLayout) findViewById(R.id.upperLayout);
            Button btnMore = (Button) findViewById(R.id.btnMore);

            txtAppTitle.setBackgroundColor(getResources().getColor(R.color.inactiveColor));
            upperLayout.setBackgroundColor(getResources().getColor(R.color.inactiveColor));
            btnMore.setBackgroundColor(getResources().getColor(R.color.inactiveColor));
        }
        else {
            intent = new Intent(this, EqualizerService.class);
            startService(intent);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            isServiceOn = true;

            TextView txtAppTitle = (TextView) findViewById(R.id.txtAppTitle);
            LinearLayout upperLayout = (LinearLayout) findViewById(R.id.upperLayout);
            Button btnMore = (Button) findViewById(R.id.btnMore);

            txtAppTitle.setBackgroundColor(getResources().getColor(R.color.activeColor));
            upperLayout.setBackgroundColor(getResources().getColor(R.color.activeColor));
            btnMore.setBackgroundColor(getResources().getColor(R.color.activeColor));

            //needs to create method for notification

            PendingIntent contentIntent =
                PendingIntent.getActivity(this, 1, new Intent(this,
                    AuremActivity.class), 0);
            notificationManager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
            int icon = R.drawable.ic_launcher;
            Notification notification = new Notification(icon,
                "AuremReloadedEQ", System.currentTimeMillis());
            Context context = getApplicationContext();
            CharSequence contentTitle = "AuremReloadedEQ";
            CharSequence contentText = getResources().getString(R.string.tapToReturn);
            notification.setLatestEventInfo(context, contentTitle,
                contentText, contentIntent);
            notification.flags |= Notification.FLAG_NO_CLEAR;
            notificationManager.notify(1, notification);

            for(short i = 0; i < 5; i++) {
                eqService.equalizer().setBandLevel(i, model.getBandLevel(i));
            }

        }

    }

    /**
     * This is called after the user selects a preset from the
     * ListActivity.
     * @param requestCode int the request code.
     * @param resultCode int the result code.
     * @param result Intent the resulting intent.
     *
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode,
        Intent result)
    {
        if(requestCode == 666 && resultCode == RESULT_OK) {
            short resultingPreset = (short) result.getIntExtra("index", 0);
            if(resultingPreset <= 9) {
                eqService.equalizer().usePreset( (short)
                    result.getIntExtra("index", 0));
                short[] bandLevels = new short[5];
                for(short i = 0; i < 5; i ++) {
                   bandLevels[i] = eqService.equalizer().getBandLevel(i);
                   model.setBandLevel(i, bandLevels[i]);
                   seekBars[i].setProgress(bandLevels[i] + 1500);
                }
            }
            else {
                Preset preset = model.getPreset((short)(resultingPreset - 10));
                short[] bands = preset.getBands();
                for(short i = 0; i < bands.length; i++) {
                    eqService.equalizer().setBandLevel(i, bands[i]);
                }
                short[] bandLevels = new short[5];
                for(short i = 0; i < 5; i ++) {
                   bandLevels[i] = eqService.equalizer().getBandLevel(i);
                   model.setBandLevel(i, bandLevels[i]);
                   seekBars[i].setProgress(bandLevels[i] + 1500);
                }
            }
        }
    }

    /**
     * Called when a new intent starts the activity.
     * @param newIntent Intent the new intent.
     */
    @Override
    public void onNewIntent(Intent newIntent)
    {
        //.
    }

    /**
     * Called when the Activity is exited.
     */
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        model.writeLastStateFile();
        unbindService(serviceConnection);
    }

    /**
     * This is called by the framework to save the state of
     * the activity. Which in our case is the band levels present
     * in the equalizer.
     */
    @Override
    public void onSaveInstanceState(Bundle bundle)
    {
        super.onSaveInstanceState(bundle);
        short[] bands = new short[5];
        for(short i = 0; i < 5; i++) {
            bands[i] = eqService.equalizer().getBandLevel(i);
        }

        bundle.putShortArray("bandLevels", bands);


    }

    /**
     * // -------------------------------------------------------------------------
    /**
     *  This is a listener that responds to changes in the five
     *  seek bars of the equalizer.
     *
     *  @author Joseph O'Connor (jto2e);
     *  @author Laura Avakian (lavakian);
     *  @author Barbara Brown (brownba1);
     *  @version 2012.04.18
     */
    public class SeekBarListener implements OnSeekBarChangeListener
    {

        /**
         * Called when any of the seekBars are changed.
         * @param seekBar SeekBar the bar.
         * @param progress int the level of the bar.
         * @param fromUser boolean true if the change comes from the user.
         */
        public void onProgressChanged(
            SeekBar seekBar,
            int progress,
            boolean fromUser)
        {
            int theProgress = progress - 1500;
            for(int i = 0; i < 5; i++) {
                if(seekBar.equals(seekBars[i])) {
                    model.setBandLevel((short) i, (short) theProgress);
                    eqService.equalizer().setBandLevel((short) i,
                        (short) theProgress);
                }
            }
        }

        /**
         * called when touch tracking starts.
         * @param seekBar the seek bar.
         */
        public void onStartTrackingTouch(SeekBar seekBar)
        {
        }

        /**
         * called when touch tracking stops.
         * @param seekBar the seek bar.
         */
        public void onStopTrackingTouch(SeekBar seekBar)
        {
        }

    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
            IBinder service) {
            eqService =
                ((EqualizerService.ServiceBinder) service).getService();
            for(short i = 0; i < 5; i ++) {
                eqService.equalizer().setBandLevel(i, model.getBandLevel(i));
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            eqService = null;
        }
    };
}