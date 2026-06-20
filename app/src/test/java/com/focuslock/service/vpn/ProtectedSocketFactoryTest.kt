package com.focuslock.service.vpn

import android.net.VpnService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.Socket
import javax.net.SocketFactory

class ProtectedSocketFactoryTest {

    private lateinit var vpnService: VpnService
    private lateinit var protectedSocketFactory: ProtectedSocketFactory
    private lateinit var dummySocket: Socket

    @Before
    fun setup() {
        vpnService = mockk<VpnService>(relaxed = true)
        dummySocket = Socket()
        protectedSocketFactory = ProtectedSocketFactory(vpnService)
    }

    @Test
    fun `createSocket no args calls protect`() {
        val mockDelegate = mockk<SocketFactory>(relaxed = true)
        every { mockDelegate.createSocket() } returns dummySocket
        
        val delegateField = ProtectedSocketFactory::class.java.getDeclaredField("delegate")
        delegateField.isAccessible = true
        delegateField.set(protectedSocketFactory, mockDelegate)

        val result = protectedSocketFactory.createSocket()
        
        assertEquals(dummySocket, result)
        verify { vpnService.protect(dummySocket) }
    }

    @Test
    fun `createSocket with host and port calls protect then connects`() {
        val mockDelegate = mockk<SocketFactory>(relaxed = true)
        val mockSocket = mockk<Socket>(relaxed = true)
        every { mockDelegate.createSocket() } returns mockSocket
        
        val delegateField = ProtectedSocketFactory::class.java.getDeclaredField("delegate")
        delegateField.isAccessible = true
        delegateField.set(protectedSocketFactory, mockDelegate)

        val result = protectedSocketFactory.createSocket("example.com", 80)
        
        assertEquals(mockSocket, result)
        verify { vpnService.protect(mockSocket) }
        verify { mockSocket.connect(any()) }
    }
}
