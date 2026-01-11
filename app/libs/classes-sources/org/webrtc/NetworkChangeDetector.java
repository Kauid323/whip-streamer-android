package org.webrtc;

import android.support.annotation.Nullable;
import java.util.List;

/* loaded from: classes.jar:org/webrtc/NetworkChangeDetector.class */
public interface NetworkChangeDetector {

    /* loaded from: classes.jar:org/webrtc/NetworkChangeDetector$ConnectionType.class */
    public enum ConnectionType {
        CONNECTION_UNKNOWN,
        CONNECTION_ETHERNET,
        CONNECTION_WIFI,
        CONNECTION_5G,
        CONNECTION_4G,
        CONNECTION_3G,
        CONNECTION_2G,
        CONNECTION_UNKNOWN_CELLULAR,
        CONNECTION_BLUETOOTH,
        CONNECTION_VPN,
        CONNECTION_NONE
    }

    /* loaded from: classes.jar:org/webrtc/NetworkChangeDetector$Observer.class */
    public interface Observer {
        void onConnectionTypeChanged(ConnectionType connectionType);

        void onNetworkConnect(NetworkInformation networkInformation);

        void onNetworkDisconnect(long j);

        void onNetworkPreference(List<ConnectionType> list, int i);
    }

    ConnectionType getCurrentConnectionType();

    boolean supportNetworkCallback();

    @Nullable
    List<NetworkInformation> getActiveNetworkList();

    void destroy();

    /* loaded from: classes.jar:org/webrtc/NetworkChangeDetector$IPAddress.class */
    public static class IPAddress {
        public final byte[] address;

        public IPAddress(byte[] address) {
            this.address = address;
        }

        @CalledByNative("IPAddress")
        private byte[] getAddress() {
            return this.address;
        }
    }

    /* loaded from: classes.jar:org/webrtc/NetworkChangeDetector$NetworkInformation.class */
    public static class NetworkInformation {
        public final String name;
        public final ConnectionType type;
        public final ConnectionType underlyingTypeForVpn;
        public final long handle;
        public final IPAddress[] ipAddresses;

        public NetworkInformation(String name, ConnectionType type, ConnectionType underlyingTypeForVpn, long handle, IPAddress[] addresses) {
            this.name = name;
            this.type = type;
            this.underlyingTypeForVpn = underlyingTypeForVpn;
            this.handle = handle;
            this.ipAddresses = addresses;
        }

        @CalledByNative("NetworkInformation")
        private IPAddress[] getIpAddresses() {
            return this.ipAddresses;
        }

        @CalledByNative("NetworkInformation")
        private ConnectionType getConnectionType() {
            return this.type;
        }

        @CalledByNative("NetworkInformation")
        private ConnectionType getUnderlyingConnectionTypeForVpn() {
            return this.underlyingTypeForVpn;
        }

        @CalledByNative("NetworkInformation")
        private long getHandle() {
            return this.handle;
        }

        @CalledByNative("NetworkInformation")
        private String getName() {
            return this.name;
        }
    }
}
