package edu.sjsu.cmpe277.androidapp;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

public class MusicPlayer extends Activity implements SensorEventListener {
	
	//music player variables
	private WakeLock wakeLock;
	private static final String[] EXTENSIONS = { ".mp3", ".mid", ".wav", ".ogg", ".mp4" };
	private List<String> trackNames;
	private List<String> trackArtworks; 
	private File path;
	private Music track;
	private ImageView bg;
	private Button btnPlay;
	private boolean isTuning;
	private int currentTrack;

	//sensor variables
	private SensorManager sensorManager;
	private Sensor accelerationSensor;
	private final float THRESHOLD = 5.0f;
	private final float NOISE = 2.0f;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "Lexiconda");
		setContentView(R.layout.main);

		initialize();
	}

	public void onResume(){
		super.onResume();
		wakeLock.acquire();
		sensorManager.registerListener(this, accelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
	}

	public void onPause(){
		super.onPause();
		sensorManager.unregisterListener(this);
		wakeLock.release();
		if(track != null){
			if(track.isPlaying()){
				track.pause();
				isTuning = false;
				btnPlay.setBackgroundResource(R.drawable.play);
			}
			if(isFinishing()){
				track.dispose();
				finish();
			}
		} else{
			if(isFinishing()){
				finish();
			}
		}
	}

	private void initialize(){
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		if (accelerationSensor != null)
			sensorManager.registerListener(this, accelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);

		bg = (ImageView) findViewById(R.id.bg);
		btnPlay = (Button) findViewById(R.id.btnPlay);
		btnPlay.setBackgroundResource(R.drawable.play);
		trackNames = new ArrayList<String>();
		trackArtworks = new ArrayList<String>();
		currentTrack = 0;
		isTuning = false;

		addTracks(getTracks());
		loadTrack();
	}

	//Generate a String Array that represents all of the files found
	private String[] getTracks(){
		if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) 
				|| Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY)){
			path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
			return path.list();
		} 
		else {
			Toast.makeText(getBaseContext(), "SD Card is either mounted elsewhere or is unusable", Toast.LENGTH_LONG).show();
		}
		return null;
	}

	//Adds the playable files to the trackNames List
	private void addTracks(String[] temp){
		if(temp != null){
			for(int i = 0; i < temp.length; i++){
				//Only accept files that have one of the extensions in the EXTENSIONS array
				if(trackChecker(temp[i])){
					trackNames.add(temp[i]);
					trackArtworks.add(temp[i].substring(0, temp[i].length()-4));
				}
			}
			Toast.makeText(getBaseContext(), "Loaded " + Integer.toString(trackNames.size()) + " Tracks", Toast.LENGTH_SHORT).show();
		}
	}

	//Checks to make sure that the track to be loaded has a correct extenson
	private boolean trackChecker(String trackToTest){
		for(int j = 0; j < EXTENSIONS.length; j++){
			if(trackToTest.contains(EXTENSIONS[j])){
				return true;
			}
		}
		return false;
	}

	//Loads the track by calling loadMusic
	private void loadTrack(){
		if(track != null){
			track.dispose();
		}
		if(trackNames.size() > 0){
			track = loadMusic();
			setImage("drawable/" + trackArtworks.get(currentTrack));
		}
	}

	//loads a Music instance using either a built in asset or an external resource
	private Music loadMusic(){
		try{
			FileInputStream fis = new FileInputStream(new File(path, trackNames.get(currentTrack)));
			FileDescriptor fileDescriptor = fis.getFD();
			return new Music(fileDescriptor);
		} catch(IOException e){
			e.printStackTrace();
			Toast.makeText(getBaseContext(), "Error Loading " + trackNames.get(currentTrack), Toast.LENGTH_LONG).show();
		}
		return null;
	}

	//Sets the background image to match the track currently playing or a default image
	private void setImage(String name) {
		int defaultImageResource = getResources().getIdentifier("drawable/defaultbg", null, getPackageName());
		if(defaultImageResource != 0){
			Drawable image = getResources().getDrawable(defaultImageResource);
			bg.setImageDrawable(image);
		}
	}

	public void click(View view){
		int id = view.getId();
		switch(id){
		case R.id.btnPlay:
			synchronized(this){
				if(isTuning){
					isTuning = false;
					btnPlay.setBackgroundResource(R.drawable.play);
					track.pause();
				} else{
					isTuning = true;
					btnPlay.setBackgroundResource(R.drawable.pause);
					playTrack();
				}
			}
			return;
		case R.id.btnPrevious:
			setTrack(0);
			loadTrack();
			playTrack();
			return;
		case R.id.btnNext:
			setTrack(1);
			loadTrack();
			playTrack();
			return;
		default:
			return;
		}
	}

	private void setTrack(int direction){
		if(direction == 0){
			currentTrack--;
			if(currentTrack < 0){
				currentTrack = trackNames.size()-1;
			}
		} else if(direction == 1){
			currentTrack++;
			if(currentTrack > trackNames.size()-1){
				currentTrack = 0;
			}
		}
	}

	//Plays the Track
	private void playTrack(){
		if(isTuning && track != null){
			track.play();
			Toast.makeText(getBaseContext(), "Playing " + trackNames.get(currentTrack).substring(0, trackNames.get(currentTrack).length()-4), Toast.LENGTH_SHORT).show();
		}
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	private float lastX;
	private float lastY;

	public void onSensorChanged(SensorEvent event) {
		float x = event.values[0];
		float y = event.values[1];

		float absX = Math.abs(x);
		float absY = Math.abs(y);

		if (absX > absY) { //prev or next song
			if (absX > THRESHOLD && (absX - NOISE > Math.abs(lastX))){
				int direction = x < 0 ? 1 : 0;
				setTrack(direction);
				loadTrack();
				playTrack();
			}
			lastX = x;
		} else { //play or stop song
			if (absY > THRESHOLD && (absY - NOISE > Math.abs(lastY))){
				if (y < 0) {
					btnPlay.setBackgroundResource(R.drawable.play);
					track.pause();
				}
				else {
					btnPlay.setBackgroundResource(R.drawable.pause);
					track.play();
				}
			}
			lastY = y;
		}
	}
}