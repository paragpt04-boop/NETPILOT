package org.jsus.netpilot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.*

data class RouterSection(val title: String, val records: List<Map<String,String>>, val error: String? = null)
data class RouterSnapshot(val host: String, val at: String, val sections: List<RouterSection>)

/** RouterOS API-SSL, command allowlist, queries only. Never saves credentials or router state. */
internal object RouterOsReader {
    private data class Query(val label: String, val path: String, val fields: String)
    private val queries = listOf(
        Query("Identidad","/system/identity/print","name"),
        Query("Sistema","/system/resource/print","uptime,version,board-name,architecture-name,cpu-load,total-memory,free-memory,total-hdd-space,free-hdd-space"),
        Query("Interfaces","/interface/print",".id,name,type,running,disabled,mtu,actual-mtu,rx-byte,tx-byte"),
        Query("Direcciones IP","/ip/address/print","address,network,interface,disabled"),
        Query("Rutas","/ip/route/print","dst-address,gateway,immediate-gw,distance,routing-table,active,disabled"),
        Query("DHCP","/ip/dhcp-server/lease/print","address,mac-address,host-name,status,server,comment"),
        Query("PPPoE / PPP","/ppp/active/print","name,address,service,uptime,caller-id"),
        Query("Hotspot","/ip/hotspot/active/print","user,address,mac-address,uptime"),
        Query("Simple Queues","/queue/simple/print","name,target,max-limit,disabled"),
        Query("WireGuard","/interface/wireguard/print","name,listen-port,mtu,disabled,running"),
        Query("Firewall Filter","/ip/firewall/filter/print","chain,action,disabled,comment,src-address,dst-address"),
        Query("Logs","/log/print","time,topics,message"),
        Query("Vecinos LAN","/ip/neighbor/print","identity,interface,address,mac-address,platform,version")
    )
    private class ApiError(message:String):Exception(message)
    suspend fun query(host:String, user:String, password:String, fingerprint:String, port:Int=8729):RouterSnapshot = withContext(Dispatchers.IO) {
        val addr=host.trim()
        require(Regex("[A-Za-z0-9.-]{1,253}").matches(addr) && !addr.startsWith("-")) {"IP o hostname inválido"}
        require(port in 1..65535){"Puerto fuera de rango"}
        require(user.isNotBlank() && password.isNotBlank()) {"Usuario y contraseña obligatorios"}
        val pin=fingerprint.filter{it.isLetterOrDigit()}.lowercase().ifBlank{null}
        require(pin==null || Regex("[0-9a-f]{64}").matches(pin)) {"Huella SHA-256 inválida"}
        val tls=SSLContext.getInstance("TLS")
        if(pin!=null) {
            val manager=object:X509TrustManager {
                override fun getAcceptedIssuers():Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain:Array<X509Certificate>, authType:String) { throw java.security.cert.CertificateException("No se usa autenticación TLS cliente") }
                override fun checkServerTrusted(chain:Array<X509Certificate>, authType:String) {
                    val cert=chain.firstOrNull() ?: throw java.security.cert.CertificateException("No hay certificado")
                    cert.checkValidity()
                    val hash=MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(""){ "%02x".format(it.toInt() and 255)}
                    if(!MessageDigest.isEqual(hash.toByteArray(StandardCharsets.US_ASCII),pin.toByteArray(StandardCharsets.US_ASCII)))
                        throw java.security.cert.CertificateException("Huella de certificado no coincide")
                }
            }
            tls.init(null,arrayOf<TrustManager>(manager),null)
        } else tls.init(null,null,null)
        val sections=mutableListOf<RouterSection>()
        (tls.socketFactory.createSocket() as SSLSocket).use {socket->
            socket.soTimeout=10000
            socket.connect(InetSocketAddress(addr,port),6000)
            socket.enabledProtocols=socket.supportedProtocols.filter{it=="TLSv1.2" || it=="TLSv1.3"}.toTypedArray()
            if(pin==null) socket.sslParameters=socket.sslParameters.apply{endpointIdentificationAlgorithm="HTTPS"}
            socket.startHandshake()
            val ins=BufferedInputStream(socket.inputStream)
            val outs=BufferedOutputStream(socket.outputStream)
            send(outs,listOf("/login","=name="+user,"=password="+password))
            receive(ins,1)
            for(q in queries) {
                try {
                    send(outs,listOf(q.path,"=.proplist="+q.fields))
                    sections+=RouterSection(q.label,receive(ins,200))
                } catch(e:ApiError) {
                    sections+=RouterSection(q.label,emptyList(),e.message)
                }
            }
        }
        RouterSnapshot(addr,java.time.Instant.now().toString(),sections)
    }

    /** Obtain the *unverified* peer certificate fingerprint without ever sending credentials.
     * A caller must show it and require explicit out-of-band verification before trusting it.
     * The probe's trust manager aborts the handshake immediately after receiving the cert. */
    suspend fun probeFingerprint(host:String,port:Int):String = withContext(Dispatchers.IO) {
        val addr=host.trim()
        require(Regex("[A-Za-z0-9.-]{1,253}").matches(addr) && !addr.startsWith("-")){"IP o hostname inválido"}
        require(port in 1..65535){"Puerto fuera de rango"}
        var peer:X509Certificate?=null
        val manager=object:X509TrustManager {
            override fun getAcceptedIssuers():Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String){
                throw java.security.cert.CertificateException("No se admiten certificados de cliente")
            }
            override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String){
                peer=chain.firstOrNull()
                throw java.security.cert.CertificateException("Requiere aprobación del certificado")
            }
        }
        val ssl=SSLContext.getInstance("TLS").apply {init(null,arrayOf<TrustManager>(manager),null)}
        (ssl.socketFactory.createSocket() as SSLSocket).use {socket->
            socket.soTimeout=10000
            socket.connect(InetSocketAddress(addr,port),6000)
            socket.enabledProtocols=socket.supportedProtocols.filter{it=="TLSv1.2" || it=="TLSv1.3"}.toTypedArray()
            try{socket.startHandshake()}catch(_:SSLException){ /* Expected: manager always rejects */ }
        }
        val cert=peer ?: throw SSLHandshakeException("El router no presentó un certificado TLS")
        cert.checkValidity()
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(""){"%02X".format(it.toInt() and 255)}
    }

    /** Synthetic protocol tests; never opens a socket. */
    internal fun readTestReplies(raw:ByteArray):List<Map<String,String>>=
        receive(BufferedInputStream(ByteArrayInputStream(raw)),200)

    private fun send(out:BufferedOutputStream,words:List<String>){
        for(w in words){
            val data=w.toByteArray(StandardCharsets.UTF_8)
            writeLen(out,data.size)
            out.write(data)
        }
        writeLen(out,0)
        out.flush()
    }
    private fun writeLen(out:BufferedOutputStream,n:Int){
        when {
            n<0x80 -> out.write(n)
            n<0x4000 -> {out.write((n shr 8) or 0x80);out.write(n and 255)}
            n<0x200000 -> {out.write((n shr 16) or 0xC0);out.write((n shr 8) and 255);out.write(n and 255)}
            n<0x10000000 -> {out.write((n shr 24) or 0xE0);out.write((n shr 16) and 255);out.write((n shr 8) and 255);out.write(n and 255)}
            else -> {out.write(0xF0);repeat(4){i->out.write((n shr (24-i*8)) and 255)}}
        }
    }
    private fun next(input:BufferedInputStream):Int = input.read().also{if(it<0)throw EOFException("RouterOS cerró la conexión")}
    private fun readLen(input:BufferedInputStream):Int{
        val a=next(input)
        return when {
            a and 0x80==0 -> a
            a and 0xC0==0x80 -> ((a and 0x3F) shl 8) or next(input)
            a and 0xE0==0xC0 -> ((a and 0x1F) shl 16) or (next(input) shl 8) or next(input)
            a and 0xF0==0xE0 -> ((a and 0x0F) shl 24) or (next(input) shl 16) or (next(input) shl 8) or next(input)
            a==0xF0 -> (next(input) shl 24) or (next(input) shl 16) or (next(input) shl 8) or next(input)
            else -> error("Longitud RouterOS no válida")
        }
    }
    private fun sentence(ins:BufferedInputStream):List<String>{
        val words=mutableListOf<String>()
        while(true){
            val n=readLen(ins)
            require(n in 0..65536){"Respuesta API demasiado grande"}
            if(n==0)return words
            require(words.size<128){"Demasiados campos"}
            val bytes=ByteArray(n)
            var cursor=0
            while(cursor<n){val got=ins.read(bytes,cursor,n-cursor);if(got<=0)throw EOFException("Respuesta incompleta");cursor+=got}
            words+=String(bytes,StandardCharsets.UTF_8)
        }
    }
    private fun receive(ins:BufferedInputStream, limit:Int):List<Map<String,String>>{
        val rows=mutableListOf<Map<String,String>>()
        var count=0
        var failure:String?=null
        while(true){
            require(++count<=25000){"Demasiadas respuestas RouterOS"}
            val words=sentence(ins)
            if(words.isEmpty())continue
            val map=linkedMapOf<String,String>()
            for(w in words.drop(1)){
                if(w.startsWith("=")){
                    val i=w.indexOf('=',1)
                    if(i>1)map[w.substring(1,i)]=w.substring(i+1)
                }
            }
            when(words.first()){
                "!done" -> { if(failure!=null)throw ApiError(failure);return rows }
                "!empty" -> Unit // RouterOS 7.18+ can return no records before !done
                "!re" -> if(rows.size<limit)rows+=map
                "!trap" -> failure=map["message"]?:"Comando no autorizado o función ausente"
                "!fatal" -> throw ApiError(map["message"]?:"La conexión fue cerrada por RouterOS")
                else -> error("Respuesta RouterOS no reconocida")
            }
        }
    }
}
