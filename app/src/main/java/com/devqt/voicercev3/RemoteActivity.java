package com.devqt.voicercev3;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.ispeech.SpeechSynthesis;
import org.ispeech.error.BusyException;
import org.ispeech.error.InvalidApiKeyException;
import org.ispeech.error.NoNetworkException;

import java.io.IOException;

import ai.api.AIConfiguration;
import ai.api.AIServiceException;
import ai.api.model.AIError;
import ai.api.model.AIResponse;
import ai.api.model.Result;
import ai.api.ui.AIButton;
import lejos.hardware.lcd.GraphicsLCD;
import lejos.hardware.lcd.TextLCD;
import lejos.remote.ev3.RemoteRequestEV3;
import lejos.robotics.RegulatedMotor;



public class RemoteActivity extends AppCompatActivity {

    static final String TAG = RemoteActivity.class.getName();

    private AIButton aiButton;
    private TextView commandText;

    private RemoteRequestEV3 ev3;

    private RegulatedMotor motorA;
    private RegulatedMotor motorB;
    private RegulatedMotor motorC;
    private RegulatedMotor motorD;

    private lejos.hardware.Audio audio;
    private TextLCD lcd;
    private GraphicsLCD graphicsLCD;
    private Button connectButton;

    private SpeechSynthesis ttsEngine;
    private volatile boolean ttsReady = false;

    private EditText addressEdit;

    private Handler handler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        commandText = (TextView) findViewById(R.id.commandText);
        connectButton = (Button) findViewById(R.id.connectButton);
        addressEdit = (EditText) findViewById(R.id.addressEdit);

        aiButton = (AIButton) findViewById(R.id.micButton);

        handler = new Handler(Looper.getMainLooper());

        initTTS();

    }

    private void initTTS() {
        try {
            ttsEngine = SpeechSynthesis.getInstance(this, this);
            ttsEngine.setVoiceType("usenglishmale");
            ttsReady = true;
        } catch (InvalidApiKeyException e) {
            Log.d(TAG, "Invalid iSpeech key", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (ev3 != null) {
            new ConnectTask().execute("disconnect");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String address = settings.getString("pref_ipAddress", "");
        addressEdit.setText(address);

        final AIConfiguration config = new AIConfiguration(
                settings.getString("pref_apiKey", "9dce7cf795894b2394caa1576c11ad97"),
                settings.getString("pref_subscriptionKey", "ca5e1561-e19b-4011-a130-f1d5004d677a"),
                AIConfiguration.SupportedLanguages.English,
                AIConfiguration.RecognitionEngine.System);

        aiButton.initialize(config);
        aiButton.setResultsListener(new AIButton.AIButtonListener() {
            @Override
            public void onResult(AIResponse aiResponse) {
                if (!aiResponse.isError()) {
                    commandText.setText(aiResponse.getResult().getAction());
                    processResponse(aiResponse);
                } else {
                    commandText.setText(aiResponse.getStatus().getErrorDetails());
                }
            }

            @Override
            public void onError(AIError aiError) {
                commandText.setText(aiError.getMessage());
            }

            @Override
            public void onCancelled() {

            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (ttsEngine != null) {
            ttsEngine.stop();
            ttsEngine = null;
            ttsReady = false;
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        initTTS();
    }

    private void processResponse(final AIResponse aiResponse) {

        if (aiResponse != null) {
            new ControlTask().execute(aiResponse);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();


        if (id == R.id.setting) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void connectButton_onClick(View view) {
        if (ev3 == null) {
            String address = addressEdit.getText().toString();
            if (!TextUtils.isEmpty(address)) {

                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString("pref_ipAddress", address);
                editor.apply();

                new ConnectTask().execute("connect", address);
            }
        } else {
            new ConnectTask().execute("disconnect");
        }
    }

    public void seetings_onClick(MenuItem item) {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void stopMotor(RegulatedMotor regulatedMotor) {
        regulatedMotor.setSpeed(0);
        regulatedMotor.stop(true);
    }

    private void fltMotor(RegulatedMotor regulatedMotor) {
        regulatedMotor.setSpeed(0);
        regulatedMotor.flt(true);
    }

    private class ConnectTask extends AsyncTask<String, Integer, Integer> {

        @Override
        protected Integer doInBackground(String... cmd) {
            switch (cmd[0]) {
                case "connect":
                    try {
                        ev3 = new RemoteRequestEV3(cmd[1]);
                        motorA = ev3.createRegulatedMotor("A", 'L');
                        motorB = ev3.createRegulatedMotor("B", 'L');
                        motorC = ev3.createRegulatedMotor("C", 'L');
                        motorD = ev3.createRegulatedMotor("D", 'L');

                        lcd = ev3.getTextLCD();

                        audio = ev3.getAudio();
                        audio.systemSound(3);

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                connectButton.setText("Disconnect");
                            }
                        });

                    } catch (IOException e) {
                        Log.e(TAG, "Can't connect to robot");
                        return 1;
                    }
                    break;

                case "disconnect":
                    if (ev3 != null) {

                        try {
                            audio.systemSound(2);

                            stopMotor(motorA);
                            motorA.close();

                            stopMotor(motorB);
                            motorB.close();

                            fltMotor(motorC);
                            motorC.close();

                            fltMotor(motorD);
                            motorD.close();

                        } catch (Exception e) {
                            Log.e(TAG, "Can't release motors", e);
                        } finally {
                            ev3.disConnect();
                            ev3 = null;
                        }
                    }

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            connectButton.setText(R.string.connect);
                        }
                    });

                    break;
            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == 1) {
                Toast.makeText(RemoteActivity.this, "Could not connect to EV3", Toast.LENGTH_LONG).show();
            } else if (result == 2) {
                Toast.makeText(RemoteActivity.this, "Not connected", Toast.LENGTH_LONG).show();
            }
        }
    }

    private class ControlTask extends AsyncTask<AIResponse, Integer, Integer> {

        private String errorMessage;

        protected Integer doInBackground(AIResponse... cmd) {

            Log.d(TAG, "Start response processing");

            if (ev3 == null) {
                return 2;
            }

            AIResponse command = cmd[0];

            if (command == null || command.isError()) {
                return 0;
            }

            try {
                ev3.getAudio().systemSound(1);

                if (!TextUtils.isEmpty(command.getResult().getFulfillment().getSpeech())) {
                    lcd.clear();
                    String speech = command.getResult().getFulfillment().getSpeech();

                    Log.d(TAG, "Write speech " + speech);

                    final int BASE_LINE = 4;
                    if (speech.length() > 15) {

                        lcd.drawString(speech.substring(0, 15), 0, BASE_LINE);

                        if (speech.length() > 15) {
                            lcd.drawString(speech.substring(15, Math.min(speech.length(), 30)), 0, BASE_LINE + 1);
                        }

                        if (speech.length() > 30) {
                            lcd.drawString(speech.substring(30, Math.min(speech.length(), 45)), 0, BASE_LINE + 2);
                        }

                        if (speech.length() > 45) {
                            lcd.drawString(speech.substring(45, Math.min(speech.length(), 60)), 0, BASE_LINE + 3);
                        }

                        if (speech.length() > 60) {
                            lcd.drawString(speech.substring(60, Math.min(speech.length(), 75)), 0, BASE_LINE + 4);
                        }

                        if (speech.length() > 75) {
                            lcd.drawString(speech.substring(75), 0, BASE_LINE + 5);
                        }

                    } else {
                        lcd.drawString(speech, 0, BASE_LINE);
                    }

                    lcd.refresh();

                    Log.d(TAG, "Speak " + speech);
                    try {
                        ttsEngine.speak(speech);
                    } catch (BusyException | NoNetworkException e) {
                        Log.e(TAG, "TTS error", e);
                    }
                }

                switch (command.getResult().getAction().toLowerCase()) {
                    case "move":
                        doMove(command);
                        break;

                    default:
                        tryNextStep(command);
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception while command execution", e);
                errorMessage = e.getMessage();
                return 3;
            }

            return 0;
        }

        private void tryNextStep(AIResponse command) {

            Log.d(TAG, "tryNextStep");

            if (command != null && !command.isError() && command.getResult() != null) {

                Log.d(TAG, "Command != null - executing command");

                if (command.getResult().getParameters() != null && !command.getResult().getParameters().isEmpty()) {
                    final float nextDelay = command.getResult().getIntParameter("nextDelay", 500);

                    try {
                        Thread.sleep(Math.round(Math.abs(nextDelay)));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    AIResponse nextCommand = null;
                    final String nextStep = command.getResult().getStringParameter("nextStep");
                    if (!TextUtils.isEmpty(nextStep)) {
                        try {
                            nextCommand = aiButton.textRequest(nextStep);
                        } catch (AIServiceException e) {
                            e.printStackTrace();
                        }
                    }

                    if (nextCommand != null) {
                        new ControlTask().execute(nextCommand);
                    }
                }
            }
        }

        private void doMove(AIResponse command) {

            Log.d(TAG, "doMove");

            final Result result = command.getResult();

            final float k = result.getFloatParameter("k", 1);
            final float nextDelay = result.getIntParameter("nextDelay", 0) * k;

            final float CANONICAL_POWER = 100.0f;

            final int aPower = result.getIntParameter("a_power");
            final float aTime = result.getFloatParameter("a_time") * k;

            final int bPower = result.getIntParameter("b_power");
            //final float bTime = result.getIntParameter("b.time") * k;

            final int cPower = result.getIntParameter("c_power");
            //final float cTime = result.getIntParameter("c.time") * k;

            final int dPower = result.getIntParameter("d_power");
            //final float dTime = result.getIntParameter("d.time") * k;

            Log.d(TAG, "Go motor A " + aPower);
            motorGo(motorA, aPower);

            Log.d(TAG, "Go motor B " + bPower);
            motorGo(motorB, bPower);

            Log.d(TAG, "Go motor C " + cPower);
            motorGo(motorC, cPower);

            Log.d(TAG, "Go motor D " + dPower);
            motorGo(motorD, dPower);

            try {
                int roundedTime = Math.round(aTime * 1000 + nextDelay);
                Log.d(TAG, "First sleep time: " + roundedTime);
                if (roundedTime > 0 && roundedTime < Integer.MAX_VALUE - 10000) {
                    Log.d(TAG, "First sleep " + roundedTime);
                    Thread.sleep(roundedTime);
                    Log.d(TAG, "End first sleep");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            AIResponse nextCommand = null;
            final String nextStep = result.getStringParameter("nextStep");
            Log.d(TAG, "Next step command: " + nextStep);
            if (!TextUtils.isEmpty(nextStep)) {
                try {
                    nextCommand = aiButton.textRequest(nextStep);
                    Log.d(TAG, "Next step received");
                } catch (AIServiceException e) {
                    e.printStackTrace();
                }
            }

            try {
                int roundedTime = Math.round(Math.abs(nextDelay));
                Log.d(TAG, "Second sleep time: " + roundedTime);
                if (roundedTime > 0 && roundedTime < Integer.MAX_VALUE - 10000) {
                    Log.d(TAG, "Second sleep " + roundedTime);
                    Thread.sleep(roundedTime);
                    Log.d(TAG, "End second sleep");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Log.d(TAG, "Trying stop all motors");

            Log.d(TAG, "Try stop A");
            stopMotor(motorA);
            Log.d(TAG, "A stopped");

            Log.d(TAG, "Try stop B");
            stopMotor(motorB);
            Log.d(TAG, "B stopped");

            Log.d(TAG, "Try stop C");
            fltMotor(motorC);
            Log.d(TAG, "C stopped");

            Log.d(TAG, "Try stop D");
            fltMotor(motorD);
            Log.d(TAG, "D stopped");

            if (nextCommand != null) {
                Log.d(TAG, "Executing next command");
                new ControlTask().execute(nextCommand);
            }

        }

        private void motorGo(RegulatedMotor regulatedMotor, int power) {
            if (regulatedMotor != null) {

                Log.d(TAG, "motorGo " + power);

                if (power > 0) {
                    regulatedMotor.setSpeed(power);
                    regulatedMotor.forward();
                } else if (power == 0) {
                    stopMotor(regulatedMotor);
                } else {
                    regulatedMotor.setSpeed(Math.abs(power));
                    regulatedMotor.backward();
                }
            }
        }

        protected void onPostExecute(Integer result) {
            if (result == 1) {
                Toast.makeText(RemoteActivity.this, "Could not connect to EV3", Toast.LENGTH_LONG).show();
            } else if (result == 2) {
                Toast.makeText(RemoteActivity.this, "Not connected", Toast.LENGTH_LONG).show();
            }
            else if (result == 3) {
                Toast.makeText(RemoteActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        }
    }

}