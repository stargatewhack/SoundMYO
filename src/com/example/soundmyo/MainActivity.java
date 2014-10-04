package com.example.soundmyo;

import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.util.Log;
import android.view.Menu;
import android.os.IBinder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import android.net.Uri;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Color;
import android.widget.ListView;
import android.widget.Toast;

import com.example.soundmyo.MusicService.MusicBinder;
import android.widget.MediaController.MediaPlayerControl;

import com.thalmic.myo.AbstractDeviceListener;
import com.thalmic.myo.Arm;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.Pose;
import com.thalmic.myo.Quaternion;
import com.thalmic.myo.XDirection;
import com.thalmic.myo.scanner.ScanActivity;

/* Code taken from the following tutorials:
 * 
 * http://code.tutsplus.com/tutorials/create-a-music-player-on-android-song-playback--mobile-22778
 * http://code.tutsplus.com/tutorials/create-a-music-player-on-android-project-setup--mobile-22764
 * http://code.tutsplus.com/tutorials/create-a-music-player-on-android-user-controls--mobile-22787
 */

/* To Do:
 * 	1. Implement Repeat function
 *  2. Get Icons for Shuffle On/Off and Repeat On/Off
 *  3. Implement MYO commands:
 *  	a) Play/Pause 		-> Gesture: Hand Spread 				~ Haptic Feedback x1
 *  	b) Back/Fwd Song 	-> Gesture: Fist + Motion Left/Right 	~ Haptic Feedback x1
 *  	c) Shuffle On/Off 	-> Gesture: Swipe Left					~ Haptic Feedback ON x1/ OFF x2
 *  	d) Repeat On/Off 	-> Gesture: Swipe Right					~ Haptic Feedback ON x1/ OFF x2
 */

public class MainActivity extends Activity implements MediaPlayerControl{

	private final String TAG = "MYOMusic";
	
	private MusicService musicSrv;
	private Intent playIntent;
	private boolean musicBound=false;
	
	private ArrayList<Song> songList;
	private ListView songView;
	
	private MusicController controller;
	
	private boolean paused=false, playbackPaused=false;
	
    // This code will be returned in onActivityResult() when the enable Bluetooth activity exits.
    private static final int REQUEST_ENABLE_BT = 1;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        
        songView = (ListView)findViewById(R.id.song_list);
        songList = new ArrayList<Song>();
        getSongList();
        
        Collections.sort(songList, new Comparator<Song>(){
        	public int compare(Song a, Song b){
        	  return a.getTitle().compareTo(b.getTitle());
        	}
        });
        
        SongAdapter songAdt = new SongAdapter(this, songList);
        songView.setAdapter(songAdt);
        setController();
        
        //For MYO, from HelloWorldActivity.java
        // First, we initialize the Hub singleton with an application identifier.
        Hub hub = Hub.getInstance();
        if (!hub.init(this, getPackageName())) {
            // We can't do anything with the Myo device if the Hub can't be initialized, so exit.
            Toast.makeText(this, "Couldn't initialize Hub", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Next, register for DeviceListener callbacks.
        hub.addListener(mListener);
    }
    
    public void getSongList() {
    	//retrieve song info
    	ContentResolver musicResolver = getContentResolver();
    	Uri musicUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    	Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);
    	if(musicCursor!=null && musicCursor.moveToFirst()){
    		//get columns
    		int titleColumn = musicCursor.getColumnIndex
    		    (android.provider.MediaStore.Audio.Media.TITLE);
    		int idColumn = musicCursor.getColumnIndex
    		    (android.provider.MediaStore.Audio.Media._ID);
    		int artistColumn = musicCursor.getColumnIndex
    		    (android.provider.MediaStore.Audio.Media.ARTIST);
    		//add songs to list
    		do {
    		    long thisId = musicCursor.getLong(idColumn);
    		    String thisTitle = musicCursor.getString(titleColumn);
    		    String thisArtist = musicCursor.getString(artistColumn);
    		    songList.add(new Song(thisId, thisTitle, thisArtist));
    		}
    		while (musicCursor.moveToNext());
    	}
    }
    
    //connect to the service
    private ServiceConnection musicConnection = new ServiceConnection(){
     
    	@Override
    	public void onServiceConnected(ComponentName name, IBinder service) {
    		MusicBinder binder = (MusicBinder)service;
    		//get service
    		musicSrv = binder.getService();
    		//pass list
    		musicSrv.setList(songList);
    		musicBound = true;
    	}
	 
    	@Override
    	public void onServiceDisconnected(ComponentName name) {
    		musicBound = false;
    	}
    };
    
    @Override
    protected void onStart() {
    	super.onStart();
    	if(playIntent==null){
    		playIntent = new Intent(this, MusicService.class);
    		bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
    		startService(playIntent);
    	}
    }

	@Override
	protected void onPause(){
		super.onPause();
		paused=true;
	}
	
	@Override
	protected void onResume(){
		super.onResume();
		
		//For MYO, from HelloWorldActivity.java
        // If Bluetooth is not enabled, request to turn it on.
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        
	  	if(paused){
		  setController();
	    	paused=false;
	  	}
	}
	
	@Override
	protected void onStop() {
		controller.hide();
		super.onStop();
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	//menu item selected
    	switch (item.getItemId()) {
    	case R.id.action_repeat:
    		musicSrv.setRepeat();
    		break;
    	case R.id.action_shuffle:
    		musicSrv.setShuffle();
    		break;
    	/*case R.id.action_end:
    		stopService(playIntent);
    		musicSrv=null;
    		System.exit(0);
    		break;*/
    	case R.id.action_scan:
            onScanActionSelected();
    		return true;
    	}
    	return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
    	stopService(playIntent);
    	musicSrv=null;
    	super.onDestroy();
    	
    	//For MYO, from HelloWorldActivity.java
        // We don't want any callbacks when the Activity is gone, so unregister the listener.
        Hub.getInstance().removeListener(mListener);

        if (isFinishing()) {
            // The Activity is finishing, so shutdown the Hub. This will disconnect from the Myo.
            Hub.getInstance().shutdown();
        }
    }
    
    public void songPicked(View view){
    	musicSrv.setSong(Integer.parseInt(view.getTag().toString()));
    	musicSrv.playSong();
    	if(playbackPaused){
    		setController();
    		playbackPaused=false;
    	}
    	controller.show(0);
    }

    //play next
    private void playNext(){
    	musicSrv.playNext();
    	if(playbackPaused){
    	    setController();
    	    playbackPaused=false;
    	}
    	controller.show(0);
    }
     
    //play previous
    private void playPrev(){
    	musicSrv.playPrev();
    	if(playbackPaused){
    	    setController();
    	    playbackPaused=false;
    	}
    	controller.show(0);
    }
    
    private void setController(){
    	//set the controller up
    	controller = new MusicController(this);
    	controller.setPrevNextListeners(new View.OnClickListener() {
    		@Override
    		public void onClick(View v) {
    			playNext();
    		}
    		}, new View.OnClickListener() {
    		  @Override
    		  public void onClick(View v) {
    		    playPrev();
    		 }
    	});
    	controller.setMediaPlayer(this);
    	controller.setAnchorView(findViewById(R.id.song_list));
    	controller.setEnabled(true);
    }
    
	@Override
	public boolean canPause() {
		return true;
	}

	@Override
	public boolean canSeekBackward() {
		return true;
	}

	@Override
	public boolean canSeekForward() {
		return true;
	}

	@Override
	public int getAudioSessionId() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getBufferPercentage() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getCurrentPosition() {
		if(musicSrv!=null && musicBound && musicSrv.isPng())
			return musicSrv.getPosn();
		else return 0;
	}

	@Override
	public int getDuration() {
		if(musicSrv!=null && musicBound && musicSrv.isPng())
			return musicSrv.getDur();
		else return 0;
	}

	@Override
	public boolean isPlaying() {
		if(musicSrv!=null && musicBound)
			return musicSrv.isPng();
		return false;
	}

	@Override
	public void pause() {
		playbackPaused=true;
		musicSrv.pausePlayer();
	}
	 
	@Override
	public void seekTo(int pos) {
		musicSrv.seek(pos);
	}
	 
	@Override
	public void start() {
		musicSrv.go();
	}
	
	public void setShuffle(){
		musicSrv.setShuffle();
	}
	
	public void setRepeat(){
		musicSrv.setRepeat();
	}
	
	//Most of the structure for the MYO portion of this code is taken from HelloWorldActivity.java
	
    // Classes that inherit from AbstractDeviceListener can be used to receive events from Myo devices.
    // If you do not override an event, the default behavior is to do nothing.
    private DeviceListener mListener = new AbstractDeviceListener() {

        private Arm mArm = Arm.UNKNOWN;
        private XDirection mXDirection = XDirection.UNKNOWN;
        private float fistRoll = 0;
        private float CW_FWD_Thresh = 30;
        private float CCW_LAST_Thresh = -30;
        
        // onConnect() is called whenever a Myo has been connected.
        @Override
        public void onConnect(Myo myo, long timestamp) {
            //Set a certain Icon to be green, to show connect
        	
        	//TO DO
        	
            Toast.makeText(getApplicationContext(), "MYO Connected",Toast.LENGTH_LONG).show();
        }

        // onDisconnect() is called whenever a Myo has been disconnected.
        @Override
        public void onDisconnect(Myo myo, long timestamp) {
            //Set a certain Icon to be red, to show disconnect
        	
        	//TO DO
        	
            Toast.makeText(getApplicationContext(), "MYO Disconnected", Toast.LENGTH_LONG).show();
        }

        // onArmRecognized() is called whenever Myo has recognized a setup gesture after someone has put it on their
        // arm. This lets Myo know which arm it's on and which way it's facing.
        @Override
        public void onArmRecognized(Myo myo, long timestamp, Arm arm, XDirection xDirection) {
            mArm = arm;
            mXDirection = xDirection;
        }

        // onArmLost() is called whenever Myo has detected that it was moved from a stable position on a person's arm after
        // it recognized the arm. Typically this happens when someone takes Myo off of their arm, but it can also happen
        // when Myo is moved around on the arm.
        @Override
        public void onArmLost(Myo myo, long timestamp) {
            mArm = Arm.UNKNOWN;
            mXDirection = XDirection.UNKNOWN;
        }

        // onOrientationData() is called whenever a Myo provides its current orientation,
        // represented as a quaternion.
        @Override
        public void onOrientationData(Myo myo, long timestamp, Quaternion rotation) {
            // Calculate Euler angles (roll, pitch, and yaw) from the quaternion.
            float roll = (float) Math.toDegrees(Quaternion.roll(rotation));
            float pitch = (float) Math.toDegrees(Quaternion.pitch(rotation));
            float yaw = (float) Math.toDegrees(Quaternion.yaw(rotation));

            // Adjust roll and pitch for the orientation of the Myo on the arm.
            if (mXDirection == XDirection.TOWARD_ELBOW) {
                roll *= -1;
                pitch *= -1;
            }

            // Continuously save the roll values for switching songs.
            fistRoll = roll;
        }

        
        // onPose() is called whenever a Myo provides a new pose.
        @Override
        public void onPose(Myo myo, long timestamp, Pose pose) {
            // Handle the cases of the Pose enumeration, and change the text of the text view
            // based on the pose we receive.
        	
            switch (pose) {
                case UNKNOWN:
                    break;
                case REST:
                    break;
                case FIST:				//Next Song, Prev Song
                   
                	if(fistRoll > CW_FWD_Thresh){
                		playNext();
                	}
                	else if(fistRoll < CCW_LAST_Thresh){
                		playPrev();
                	}
                    break;
                    
                case WAVE_IN:			//Shuffle
                	setShuffle();
                    break;
                case WAVE_OUT:			//Repeat
                    setRepeat();
                    break;
                case FINGERS_SPREAD: 	// Play/Pause
                	
                    if(isPlaying()){
                    	pause();
                    }
                    else{
                    	start();
                    }
                    break;
                    
                case THUMB_TO_PINKY:
                    
                    break;
            }
        }
    };
	
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth, so exit.
        /*if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }*/
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void onScanActionSelected() {
        // Launch the ScanActivity to scan for Myos to connect to.
        Intent intent = new Intent(this, ScanActivity.class);
        startActivity(intent);
    }

}
