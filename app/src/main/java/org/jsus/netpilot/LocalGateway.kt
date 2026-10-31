package org.jsus.netpilot

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** The Wi-Fi default gateway is a hint, NOT proof that it is the MikroTik. */
internal object LocalGateway {
    fun probableRouter(context:Context):String? {
        val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return try {
            cm.allNetworks.asSequence()
                .filter{cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true}
                .mapNotNull{cm.getLinkProperties(it)}
                .flatMap{it.routes.asSequence()}
                .firstOrNull{route->
                    route.isDefaultRoute && route.gateway is Inet4Address
                }?.gateway?.hostAddress
        }catch(_:SecurityException){null}
    }
}
