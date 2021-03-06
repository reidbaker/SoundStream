/*
 * Copyright 2013 The Last Crusade ContactLastCrusade@gmail.com
 * 
 * This file is part of SoundStream.
 * 
 * SoundStream is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SoundStream is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with SoundStream.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.thelastcrusade.soundstream.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.thelastcrusade.soundstream.R;
import com.thelastcrusade.soundstream.library.MediaStoreWrapper;
import com.thelastcrusade.soundstream.library.SongNotFoundException;
import com.thelastcrusade.soundstream.model.SongMetadata;
import com.thelastcrusade.soundstream.model.UserList;
import com.thelastcrusade.soundstream.service.MessagingService.MessagingServiceBinder;
import com.thelastcrusade.soundstream.service.ServiceLocator.IOnBindListener;
import com.thelastcrusade.soundstream.util.AlphabeticalComparator;
import com.thelastcrusade.soundstream.util.BroadcastRegistrar;
import com.thelastcrusade.soundstream.util.IBroadcastActionHandler;
import com.thelastcrusade.soundstream.util.LocalBroadcastIntent;
import com.thelastcrusade.soundstream.util.SongMetadataUtils;

public class MusicLibraryService extends Service {
    
    private static String TAG = MusicLibraryService.class.getSimpleName();

    /**
     * Broadcast action sent when the MusicLibrary gets or loses music
     *
     */
    public static final String ACTION_LIBRARY_UPDATED = MusicLibraryService.class
            .getName() + ".action.LibraryUpdated";

    /**
     * A map of keys to array positions, and an array of song metadata.
     * 
     * This lets us maintain the order in which things are added, but also allows
     * us to quickly account for duplicates/replace with updated data.
     * 
     * This is also the solution to a common interview question :-)
     * 
     */
    private Map<String, Integer> metadataMap  = new HashMap<String, Integer>();
    private List<SongMetadata>   metadataList = new ArrayList<SongMetadata>();
    
    private final Object metadataMutex = new Object();
    
    private BroadcastRegistrar registrar;

    private String myMacAddress;

    private ServiceLocator<MessagingService> messagingServiceLocator;
    private ServiceLocator<UserListService> userListServiceLocator;

    public class MusicLibraryServiceBinder extends Binder implements
        ILocalBinder<MusicLibraryService> {
        public MusicLibraryService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MusicLibraryService.this;
        }
    }

    @Override
    public void onCreate() {
        userListServiceLocator = new ServiceLocator<UserListService>(
                this, UserListService.class, UserListService.UserListServiceBinder.class);
        
        myMacAddress = getResources().getString(R.string.default_mac);
        userListServiceLocator.setOnBindListener(new IOnBindListener() {
            @Override
            public void onServiceBound() {
                myMacAddress = getMyMac();
                //load the local songs and set the mac address, so the metadata objects
                // can live in the library
                List<SongMetadata> metadataList = (new MediaStoreWrapper(MusicLibraryService.this)).list();
                for (SongMetadata song : metadataList) {
                    song.setMacAddress(myMacAddress);
                }

                //update the library with the local songs
                updateLibrary(metadataList, false);
            }
        });

        messagingServiceLocator = new ServiceLocator<MessagingService>(
                this, MessagingService.class, MessagingServiceBinder.class);

        registerReceivers();
    }

    @Override
    public void onDestroy() {
        unregisterReceivers();
        messagingServiceLocator.unbind();
        userListServiceLocator.unbind();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new MusicLibraryServiceBinder();
    }
    
    /**
     * 
     */
    private void registerReceivers() {
        this.registrar = new BroadcastRegistrar();
        this.registrar
            .addLocalAction(MessagingService.ACTION_LIBRARY_MESSAGE, new IBroadcastActionHandler() {
                
                @Override
                public void onReceiveAction(Context context, Intent intent) {
                    List<SongMetadata> remoteMetas = intent.getParcelableArrayListExtra(MessagingService.EXTRA_SONG_METADATA);
                    updateLibrary(remoteMetas, true);
                }
            })
            .addLocalAction(UserList.ACTION_USER_LIST_UPDATE, new IBroadcastActionHandler() {

                @Override
                public void onReceiveAction(Context context, Intent intent) {
                    /*
                     *  When we get a updated user list message we calculate the users that were removed.
                     *  Here we loop through the removed users and remove any songs that belong to disconnected users.
                    */
                    UserList removedUsers = (UserList) intent.getParcelableExtra(UserList.EXTRA_REMOVED_USERS);
                    if(removedUsers != null) {
                        for(String mac : removedUsers.getMacAddresses()){
                            removeLibraryForAddress(mac, true);
                        }
                    }
                }
            })
            .addLocalAction(ConnectionService.ACTION_GUEST_DISCONNECTED, new IBroadcastActionHandler() {

                @Override
                public void onReceiveAction(Context context, Intent intent) {
                    String macAddress = intent.getStringExtra(ConnectionService.EXTRA_GUEST_ADDRESS);
                    Log.w(TAG, macAddress +" disconnected");
                    removeLibraryForAddress(macAddress, true);
                }
            })
            .register(this);
    }

    private void unregisterReceivers() {
        this.registrar.unregister();
    }
    /** Methods for clients */

    public List<SongMetadata> getLibrary() {
        return getLibrary(null);
    }
    
    public List<SongMetadata> getLibrary(String query) {     
        ArrayList<SongMetadata> filtered = new ArrayList<SongMetadata>();
        
        synchronized(metadataMutex) {
            if (query == null) {
                //unmodifiable copy, for safety
                filtered = new ArrayList<SongMetadata>(metadataList);
            } else {
                for(SongMetadata data : metadataList) {
                    if(songMatchesQuery(query, data)) {
                        filtered.add(data);
                    } 
                }                    
            }
            return Collections.unmodifiableList(filtered);
        }
    }

    /**
     * @param query
     * @param data
     * @return
     */
    private boolean songMatchesQuery(String query, SongMetadata data) {
        return data.getTitle().toLowerCase().contains(query.toLowerCase())
           || data.getAlbum().toLowerCase().contains(query.toLowerCase())
           || data.getArtist().toLowerCase().contains(query.toLowerCase());
    }

    public List<SongMetadata> getMyLibrary() {
        //unmodifiable copy, for safety
        return Collections.unmodifiableList(getMyModifiableLibrary());
    }

    private List<SongMetadata> getMyModifiableLibrary() {
        synchronized(metadataMutex) {
            List<SongMetadata> myLibrary = new ArrayList<SongMetadata>();
            //look thru the library, and pull out songs with "my" mac address
            for (SongMetadata meta : metadataList) {
                if (meta.getMacAddress().equals(this.myMacAddress)) {
                    myLibrary.add(meta);
                }
            }
            return myLibrary;
        }
    }

    /**
     * Update the library with the additional songs passed in.
     * 
     * NOTE: This should not be called by an outside user.  It is package protected to allow us to unit test
     * it, but generally speaking, the library gets updated from network messages and the onCreate method.
     * 
     * @param additionalSongs
     * @param notify
     */
    void updateLibrary(Collection<SongMetadata> additionalSongs, boolean notify) {
        synchronized(metadataMutex) {
            for (SongMetadata song : additionalSongs) {
                String key = SongMetadataUtils.getUniqueKey(song);
                if (metadataMap.containsKey(key)) {
                    //song already exists, replace the existing entry in the list with the new data.
                    metadataList.set(metadataMap.get(key), song);
                } else {
                    //new song to add to the list...add it, and store the position in the map
                    int nextInx = metadataList.size();
                    metadataList.add(song);
                    metadataMap.put(key, nextInx);
                }
            }
            
            /*
             * by default we want to order alphabetically
             * when we have more options, this can be moved elsewhere
             * and governed by some type of flag.
             */
            orderAlphabetically();
        }
        if (notify) {
            notifyLibraryUpdated();
        }
    }

    /**
     * Notify that the library was updated.  This includes
     * sending an intent to the system, and sending the library out
     * to the guests.
     */
    private void notifyLibraryUpdated() {
        new LocalBroadcastIntent(ACTION_LIBRARY_UPDATED).send(this);
        //send the updated library to all the guests out there
        if (getMessagingService() != null) {
            getMessagingService().sendLibraryMessageToGuests(getLibrary());
        }
    }

    /**
     * Remove all songs that belong to the specified mac address.
     * 
     * NOTE: This should not be called by an outside user.  It is package protected to allow us to unit test
     * it, but generally speaking, the library gets updated from network messages and the onCreate method.
     * 
     * @param additionalSongs
     * @param notify
     */
    void removeLibraryForAddress(String macAddress, boolean notify) {
        synchronized(metadataMutex) {
            //remove the songs for the specified address by assembling a new list
            // and map with all songs except those for that address
            List<SongMetadata>   newList = new ArrayList<SongMetadata>();
            Map<String, Integer> newMap  = new HashMap<String, Integer>();
            for (SongMetadata song : metadataList) {
                if (!song.getMacAddress().equals(macAddress)) {
                    int nextInx = newList.size();
                    newList.add(song);
                    String key = SongMetadataUtils.getUniqueKey(song);
                    newMap.put(key, nextInx);
                }
            }
            //replace THE list and map with the new structures
            metadataList = newList;
            metadataMap = newMap;
        }
        if (notify) {
            notifyLibraryUpdated();
        }
    }

    /**
     * Orders the song metadata and related map alphabetically by Artist,
     * Album, and Title
     */
    private void orderAlphabetically(){
        synchronized(metadataMutex) {
            //sort the metadata alphabetically
            Collections.sort(metadataList, new AlphabeticalComparator());
           
            //recreate the map
            Map<String, Integer> newMap  = new HashMap<String, Integer>();
            for(int i=0; i<metadataList.size(); i++){
                String key = SongMetadataUtils.getUniqueKey(metadataList.get(i));
                newMap.put(key, i);
            }
            metadataMap = newMap;
        }
    }

    public SongMetadata lookupSongByAddressAndId(String address, long songId) {
        synchronized(metadataMutex) {
            //TODO: remove use of bluetoothutils...replace with reference to userlist or some other way
            // of getting "my" address
            String key = SongMetadataUtils.getUniqueKey(address, songId);
            Integer inx = metadataMap.get(key);
            return inx != null ? metadataList.get(inx) : null;
        }
    }
    private SongMetadata lookupMySongById(long songId) {
        synchronized(metadataMutex) {
            //TODO: remove use of bluetoothutils...replace with reference to userlist or some other way
            // of getting "my" address
            String key = SongMetadataUtils.getUniqueKey(myMacAddress, songId);
            Integer inx = metadataMap.get(key);
            return inx != null ? metadataList.get(inx) : null;
        }
    }

    /**
     * Get the path on the local file system for the song identified by songId
     * 
     * @param songId
     * @return A string path to the song
     * @throws SongNotFoundException 
     */
    public String getSongFilePath(long songId) throws SongNotFoundException {
        MediaStoreWrapper msw = new  MediaStoreWrapper(MusicLibraryService.this);
        SongMetadata song = lookupMySongById(songId);
        return msw.getSongFilePath(song);
    }

    private String getMyMac(){
        String myMac;
        UserListService userService = getUserListService();
        if(userService != null){
            myMac = userService.getMyMac();
        } else {
            myMac = getResources().getString(R.string.default_mac);
            Log.w(TAG, "UserListService null, returning fake mac: " + myMac);
        }
        return myMac;
    }

    private UserListService getUserListService(){
        UserListService userService = null;
        try{
            userService = userListServiceLocator.getService();
        } catch (ServiceNotBoundException e) {
            Log.w(TAG, "UserListService not bound");
        }
        return userService;
    }

    private IMessagingService getMessagingService() {
        MessagingService messagingService = null;
        try {
            messagingService = this.messagingServiceLocator.getService();
        } catch (ServiceNotBoundException e) {
            Log.wtf(TAG, e);
        }
        return messagingService;
    }

    public void clearExternalMusic() {
        metadataList = getMyModifiableLibrary();
        metadataMap.clear();
        orderAlphabetically();
        new LocalBroadcastIntent(ACTION_LIBRARY_UPDATED).send(this);
    }
}
