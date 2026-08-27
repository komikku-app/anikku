package app.anikku.macos.platform.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * DNS-over-HTTPS provider constants and OkHttp builder extensions.
 * Pure OkHttp/JVM code — identical to the Android version.
 */

const val PREF_DOH_CLOUDFLARE = 1
const val PREF_DOH_GOOGLE = 2
const val PREF_DOH_ADGUARD = 3
const val PREF_DOH_QUAD9 = 4
const val PREF_DOH_ALIDNS = 5
const val PREF_DOH_DNSPOD = 6
const val PREF_DOH_360 = 7
const val PREF_DOH_QUAD101 = 8
const val PREF_DOH_MULLVAD = 9
const val PREF_DOH_CONTROLD = 10
const val PREF_DOH_NJALLA = 11
const val PREF_DOH_SHECAN = 12
const val PREF_DOH_LIBREDNS = 13

fun OkHttpClient.Builder.dohCloudflare() = dns(DnsOverHttps.Builder().client(build())
    .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
    .bootstrapDnsHosts(
        InetAddress.getByName("162.159.36.1"), InetAddress.getByName("162.159.46.1"),
        InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"),
    ).build())

fun OkHttpClient.Builder.dohGoogle() = dns(DnsOverHttps.Builder().client(build())
    .url("https://dns.google/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("8.8.4.4"), InetAddress.getByName("8.8.8.8"))
    .build())

fun OkHttpClient.Builder.dohAdGuard() = dns(DnsOverHttps.Builder().client(build())
    .url("https://dns-unfiltered.adguard.com/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("94.140.14.140"), InetAddress.getByName("94.140.14.141"))
    .build())

fun OkHttpClient.Builder.dohQuad9() = dns(DnsOverHttps.Builder().client(build())
    .url("https://dns.quad9.net/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("9.9.9.9"), InetAddress.getByName("149.112.112.112"))
    .build())

fun OkHttpClient.Builder.dohAliDNS() = dns(DnsOverHttps.Builder().client(build())
    .url("https://dns.alidns.com/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("223.5.5.5"), InetAddress.getByName("223.6.6.6"))
    .build())

fun OkHttpClient.Builder.dohDNSPod() = dns(DnsOverHttps.Builder().client(build())
    .url("https://doh.pub/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("1.12.12.12"), InetAddress.getByName("120.53.53.53"))
    .build())

fun OkHttpClient.Builder.doh360() = dns(DnsOverHttps.Builder().client(build())
    .url("https://doh.360.cn/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("101.226.4.6"), InetAddress.getByName("218.30.118.6"))
    .build())

fun OkHttpClient.Builder.dohQuad101() = dns(DnsOverHttps.Builder().client(build())
    .url("https://dns.twnic.tw/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("101.101.101.101"))
    .build())

fun OkHttpClient.Builder.dohMullvad() = dns(DnsOverHttps.Builder().client(build())
    .url("https://dns.mullvad.net/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("194.242.2.2"))
    .build())

fun OkHttpClient.Builder.dohControlD() = dns(DnsOverHttps.Builder().client(build())
    .url("https://freedns.controld.com/p0".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("76.76.2.0"), InetAddress.getByName("76.76.10.0"))
    .build())

fun OkHttpClient.Builder.dohNajalla() = dns(DnsOverHttps.Builder().client(build())
    .url("https://dns.njal.la/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("95.215.19.53"))
    .build())

fun OkHttpClient.Builder.dohShecan() = dns(DnsOverHttps.Builder().client(build())
    .url("https://free.shecan.ir/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("178.22.122.100"), InetAddress.getByName("185.51.200.2"))
    .build())

fun OkHttpClient.Builder.dohLibreDNS() = dns(DnsOverHttps.Builder().client(build())
    .url("https://doh.libredns.gr/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("116.202.176.26"))
    .build())
