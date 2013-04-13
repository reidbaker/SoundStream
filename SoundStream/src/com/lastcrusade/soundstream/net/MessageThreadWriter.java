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

package com.lastcrusade.soundstream.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.PriorityQueue;

import android.util.Log;

import com.lastcrusade.soundstream.net.message.IMessage;
import com.lastcrusade.soundstream.net.message.TransferSongMessage;
import com.lastcrusade.soundstream.net.wire.Messenger;
import com.lastcrusade.soundstream.util.LogUtil;

/**
 * A class to manage writing messages from the MessageThread.  This class
 * maintains a priority queue the prioritizes command messages over transfer
 * data messages, to ensure the user interface is snappy.
 * 
 * @author Jesse Rosalia
 *
 */
public class MessageThreadWriter {

    private static String TAG = MessageThreadWriter.class.getSimpleName();

    /**
     * Maximum size in bytes to write to a socket at a time.
     * 
     */
    private byte[] outBytes;

    class QueueEntry {
        private int messageNo;
        private int score;
        public Class<? extends IMessage> messageClass;
        public InputStream messageStream;
    }

    PriorityQueue<QueueEntry> queue = new PriorityQueue<QueueEntry>(11, new Comparator<QueueEntry>() {

        @Override
        public int compare(QueueEntry lhs, QueueEntry rhs) {
            return lhs.score - rhs.score;
        }
    });

    private OutputStream outStream;

    private Messenger messenger;
    
    public MessageThreadWriter(Messenger messenger, OutputStream outStream) {
        this.outStream = outStream;
        this.messenger = messenger;
        this.outBytes = new byte[messenger.getSendPacketSize()];
    }

    public void enqueue(int messageNo, IMessage message) throws IOException {
        QueueEntry qe = new QueueEntry();
        qe.messageNo     = messageNo;
        qe.score         = messageNo * (TransferSongMessage.class.isAssignableFrom(message.getClass()) ? 100 : 1);
        qe.messageClass  = message.getClass();
        qe.messageStream = messenger.serializeMessage(message);
        queue.add(qe);
    }

    public boolean canWrite() {
        return !queue.isEmpty();
    }

    public void writeOne() throws IOException {
        QueueEntry qe = queue.poll();
        if (qe != null) {
            int read = qe.messageStream.read(outBytes);
            if (LogUtil.isLogAvailable()) {
                Log.i(TAG, "Message " + qe.messageNo + " written, it's a " + qe.messageClass.getSimpleName() + ", " + read + " bytes in length");
            }
            outStream.write(outBytes, 0, read);
            int left = qe.messageStream.available();
            //if there are bytes left to write, add this message back into the queue
            // to write at the next opportunity
            if (left > 0) {
                if (LogUtil.isLogAvailable()) {
                    Log.i(TAG, "Message " + qe.messageNo + ", " + left + " bytes left to write");
                }
                queue.add(qe);
            } else {
                //otherwise, we're done
                if (LogUtil.isLogAvailable()) {
                    Log.i(TAG, "Message " + qe.messageNo + " finished writing");
                }
            }
        }
    }
}
