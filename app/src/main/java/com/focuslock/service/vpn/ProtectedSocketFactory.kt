package com.focuslock.service.vpn

import android.net.VpnService
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

class ProtectedSocketFactory(
    private val vpnService: VpnService
) : SocketFactory() {
    private val delegate = SocketFactory.getDefault()
    
    override fun createSocket(): Socket {
        val socket = delegate.createSocket()
        vpnService.protect(socket)
        return socket
    }

    override fun createSocket(host: String, port: Int): Socket {
        // Cannot protect an already-connected socket; create unconnected and connect after protect
        val socket = delegate.createSocket()
        vpnService.protect(socket)
        socket.connect(java.net.InetSocketAddress(host, port))
        return socket
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        val socket = delegate.createSocket()
        vpnService.protect(socket)
        socket.bind(java.net.InetSocketAddress(localHost, localPort))
        socket.connect(java.net.InetSocketAddress(host, port))
        return socket
    }

    override fun createSocket(host: InetAddress, port: Int): Socket {
        val socket = delegate.createSocket()
        vpnService.protect(socket)
        socket.connect(java.net.InetSocketAddress(host, port))
        return socket
    }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        val socket = delegate.createSocket()
        vpnService.protect(socket)
        socket.bind(java.net.InetSocketAddress(localAddress, localPort))
        socket.connect(java.net.InetSocketAddress(address, port))
        return socket
    }
}
