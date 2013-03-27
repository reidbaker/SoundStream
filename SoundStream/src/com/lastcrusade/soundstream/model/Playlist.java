package com.lastcrusade.soundstream.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import android.util.Log;

/**
 * A data structure for holding the playlist.  It keeps track of two queues of PlaylistEntry
 * objects; the seam between them represents the current play position.
 * 
 * @author Jesse Rosalia
 *
 */
public class Playlist {
    
    private final static String TAG = Playlist.class.getName();
    
    private Queue<PlaylistEntry> playedList;
    private Queue<PlaylistEntry> musicList;

    public Playlist() {
        playedList = new LinkedList<PlaylistEntry>();
        musicList  = new LinkedList<PlaylistEntry>();
    }

    public void add(PlaylistEntry entry) {
        musicList.add(entry);
    }

    public void clear() {
        playedList.clear();
        musicList.clear();
    }

    public SongMetadata remove(SongMetadata meta) {
        boolean success;
        SongMetadata removeMeta = meta;
        
        if(musicList.contains(meta)) {
            success = musicList.remove(meta);
        } else if(playedList.contains(meta)){
            success = playedList.remove(meta);
        } else {
            success = false; 
            Log.wtf(TAG, "Asked to remove unknown object");
        }

        if(!success){
            removeMeta = null;
        }
        return removeMeta;
    }

    public List<PlaylistEntry> getSongsToPlay() {
        List<PlaylistEntry> songsToPlay = new ArrayList<PlaylistEntry>();
        songsToPlay.addAll(playedList);
        songsToPlay.addAll(musicList);
        return songsToPlay;
    }

    public int size(){
        return playedList.size() + musicList.size();
    }

    public PlaylistEntry getNextAvailableSong() {
        PlaylistEntry nextAvail = null;
        for (PlaylistEntry entry : musicList) {
            if (entry.isLoaded()) {
                nextAvail = entry;
                break;
            }
        }
        
        if (nextAvail != null) {
            musicList.remove(nextAvail);
            playedList.add(nextAvail);
        }
        return nextAvail;
    }

    public void reset() {
        playedList.addAll(musicList);
        musicList = playedList;
        playedList = new LinkedList<PlaylistEntry>();
        //reset the play status on all of the entries
        for (PlaylistEntry entry : musicList) {
            entry.setPlayed(false);
        }
    }
    
    public void bumpSong(PlaylistEntry entry){
        if(musicList.contains(entry)){
            //remove the entry from the queue
            musicList.remove(entry);
            //make a new queue
            Queue<PlaylistEntry> newMusicList = new LinkedList<PlaylistEntry>();
            newMusicList.add(entry);
            newMusicList.addAll(musicList);
            musicList = newMusicList;
        }
            
    }
}
