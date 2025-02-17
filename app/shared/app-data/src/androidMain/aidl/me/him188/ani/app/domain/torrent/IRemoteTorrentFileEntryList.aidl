// ITorrentFileEntryStatsCallback.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntry;

// Declare any non-default types here with import statements

interface IRemoteTorrentFileEntryList {
    IRemoteTorrentFileEntry get(int index);
    
    int getSize();
}