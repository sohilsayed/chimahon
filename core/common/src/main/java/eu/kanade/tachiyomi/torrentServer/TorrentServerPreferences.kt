package eu.kanade.tachiyomi.torrentServer

import tachiyomi.core.common.preference.PreferenceStore

class TorrentServerPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun port() = preferenceStore.getString("pref_torrent_port", "8090")

    fun trackers() = preferenceStore.getString(
        "pref_torrent_trackers",
        """http://nyaa.tracker.wf:7777/announce
http://anidex.moe:6969/announce
http://tracker.anirena.com:80/announce
udp://tracker.uw0.xyz:6969/announce
udp://exodus.desync.com:6969/announce
udp://explodie.org:6969/announce
udp://open.stealth.si:80/announce
udp://opentracker.i2p.rocks:6969/announce
udp://tracker.cyberia.is:6969/announce
udp://tracker.dler.org:6969/announce
udp://tracker.openbittorrent.com:6969/announce
udp://tracker.opentrackr.org:1337/announce
udp://tracker.torrent.eu.org:451/announce""",
    )
}
