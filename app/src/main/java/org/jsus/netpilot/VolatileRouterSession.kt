package org.jsus.netpilot

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ephemeral session, not persisted. AES-GCM ciphertext and its key reside only in process RAM.
 * This protects against accidental plaintext persistence, not against process-memory compromise.
 * JVM String and TLS copies cannot be guaranteed to be zeroed.
 */
class VolatileRouterSession {
    private var key:ByteArray?=null
    private var iv:ByteArray?=null
    private var encrypted:ByteArray?=null
    private var version=0L
    var connected by mutableStateOf(false); private set
    var snapshot by mutableStateOf<RouterSnapshot?>(null); private set
    var loading by mutableStateOf(false); private set
    var problem by mutableStateOf(""); private set
    val traffic=mutableStateListOf<Pair<Double,Double>>()
    private var prior:Triple<Long,Long,Long>?=null
    val host:String get()=runCatching{unseal()?.getOrNull(0)?:"—"}.getOrDefault("—")
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)

    suspend fun connect(address:String,port:Int,username:String,password:String,fingerprint:String){
        require(port in 1..65535){"Puerto TLS inválido"}
        require(listOf(address,username,password,fingerprint).none{it.contains('\u0000')}){"Dato de conexión inválido"}
        val token=++version
        loading=true;problem=""
        try {
            val fresh=RouterOsReader.query(address,username,password,fingerprint,port)
            if(version==token) {
                seal(listOf(address,port.toString(),username,password,fingerprint))
                snapshot=fresh;connected=true;record(fresh)
            }
        }catch(e:CancellationException){throw e}
        catch(e:Exception){if(version==token){problem=safeError(e);connected=false};throw e}
        finally{loading=false}
    }
    suspend fun refresh(){
        if(!connected || loading)return
        val token=version
        loading=true;problem=""
        try {
            val p=unseal() ?: return
            val fresh=RouterOsReader.query(p[0],p[2],p[3],p[4],p[1].toInt())
            if(token==version){snapshot=fresh;record(fresh)}
        }catch(e:CancellationException){throw e}
        catch(e:Exception){if(token==version)problem=safeError(e)}
        finally{loading=false}
    }
    fun refreshAsync(){scope.launch{refresh()}}
    private fun record(s:RouterSnapshot){
        val ints=s.sections.firstOrNull{it.title=="Interfaces"}?.records.orEmpty()
        // Exclude virtual interfaces so the same packet is not counted through bridge/VLAN layers.
        val physical=ints.filter{
            val type=it["type"].orEmpty()
            type=="ether" || type=="wlan" || type=="wifi" || type=="lte"
        }
        if(physical.isEmpty())return
        val rx=physical.sumOf{it["rx-byte"]?.toLongOrNull()?:0}
        val tx=physical.sumOf{it["tx-byte"]?.toLongOrNull()?:0}
        val now=android.os.SystemClock.elapsedRealtime()
        prior?.let{old->
            val seconds=(now-old.first)/1000.0
            if(seconds>0.5 && rx>=old.second && tx>=old.third){
                traffic.add(Pair((rx-old.second)*8/seconds/1e6,(tx-old.third)*8/seconds/1e6))
                while(traffic.size>24)traffic.removeAt(0)
            }else if(rx<old.second || tx<old.third)traffic.clear()
        }
        prior=Triple(now,rx,tx)
    }
    fun disconnect(){
        version++
        key?.fill(0);iv?.fill(0);encrypted?.fill(0)
        key=null;iv=null;encrypted=null
        snapshot=null;connected=false;loading=false;problem=""
        traffic.clear();prior=null
    }
    private fun seal(parts:List<String>){
        val source=parts.joinToString("\u0000").toByteArray(Charsets.UTF_8)
        val k=ByteArray(32).also{SecureRandom().nextBytes(it)}
        val nonce=ByteArray(12).also{SecureRandom().nextBytes(it)}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(k,"AES"),GCMParameterSpec(128,nonce))
        val result=cipher.doFinal(source);source.fill(0)
        key?.fill(0);iv?.fill(0);encrypted?.fill(0)
        key=k;iv=nonce;encrypted=result
    }
    private fun unseal():List<String>?{
        val k=key?:return null;val nonce=iv?:return null;val bytes=encrypted?:return null
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(k,"AES"),GCMParameterSpec(128,nonce))
        val result=cipher.doFinal(bytes)
        return try {String(result,Charsets.UTF_8).split('\u0000')} finally{result.fill(0)}
    }
    private fun safeError(e:Exception):String=when(e){
        is javax.net.ssl.SSLException -> "La verificación TLS falló. Comprueba certificado y huella."
        is java.net.SocketTimeoutException -> "El router no respondió antes del tiempo límite."
        is java.net.ConnectException -> "No se pudo conectar al puerto API-SSL en la LAN."
        else -> "La consulta falló: "+(e.message?:"sin detalle")
    }
}
