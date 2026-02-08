package com.flatide.floodgate.core

import java.net.InetAddress
import java.net.NetworkInterface

class FloodgateEnv private constructor() {
    var address: String = ""
        get() {
            if (field.isEmpty()) {
                field = getLocalIp()
                addressLast = field.substring(field.lastIndexOf(".") + 1)
            }
            return field
        }
    var addressLast: String = ""
    var port: String = ""

    companion object {
        private val lazyInstance: FloodgateEnv by lazy { FloodgateEnv() }

        fun getInstance(): FloodgateEnv = lazyInstance
    }

    fun getAddressLat(): String {
        if (this.addressLast.isEmpty()) {
            this.address = getLocalIp()
            this.addressLast = this.address.substring(this.address.lastIndexOf(".") + 1)
        }

        return String.format("%03d", Integer.parseInt(this.addressLast))
    }

    fun getAddressLastWithPort(): String {
        if (this.addressLast.isEmpty()) {
            this.address = getLocalIp()
            this.addressLast = this.address.substring(this.address.lastIndexOf(".") + 1)
        }

        return String.format("%03d%05d", Integer.parseInt(this.addressLast), Integer.parseInt(this.port))
    }

    private fun getLocalIp(): String {
        var ip = ""
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    //System.out.println(inetAddress.toString());
                    if (!inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress && inetAddress.isSiteLocalAddress) {
                        ip = inetAddress.toString()
                        //return inetAddress.toString();
                    }
                }
            }
            if (ip.isNotEmpty()) {
                return ip
            }
            return InetAddress.getLocalHost().hostAddress
        } catch (e: Exception) {
        }

        return ""
    }
}
