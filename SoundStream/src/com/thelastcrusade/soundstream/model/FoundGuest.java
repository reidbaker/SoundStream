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

package com.thelastcrusade.soundstream.model;

import com.thelastcrusade.soundstream.util.DefaultParcelableCreator;

import android.os.Parcel;
import android.os.Parcelable;

public class FoundGuest implements Parcelable {

    //this is REQUIRED for Parcelable to work properly
    public static final Parcelable.Creator<FoundGuest> CREATOR = new DefaultParcelableCreator(FoundGuest.class);

    private String name;
    private String address;
    private boolean known;

    public FoundGuest(String name, String address, boolean known) {
        this.name = name;
        this.address = address;
        this.known = known;
    }
    
    public FoundGuest(Parcel in) {
        this.name    = in.readString();
        this.address = in.readString();
        this.known   = in.readInt() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getName());
        dest.writeString(getAddress());
        dest.writeInt(this.known ? 1 : 0);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isKnown() {
        return known;
    }
    
    public void setKnown(boolean known) {
        this.known = known;
    }
    
    @Override
    public String toString() {
        return this.getName() + " (" + this.getAddress() + ")";
    }
    
    //NOTE: hashCode and equals generated by Eclipse
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((address == null) ? 0 : address.hashCode());
        result = prime * result + (known ? 1231 : 1237);
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof FoundGuest))
            return false;
        FoundGuest other = (FoundGuest) obj;
        if (address == null) {
            if (other.address != null)
                return false;
        } else if (!address.equals(other.address))
            return false;
        if (known != other.known)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }
}