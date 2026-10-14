package com.company.itam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@CrossOrigin(origins = "*")
@Tag(name = "System Info", description = "Server network addresses and system discovery")
public class SystemController {

    @Value("${server.port:8085}")
    private int serverPort;

    @Value("${itam.server.host:}")
    private String configuredHost;

    @GetMapping("/network-info")
    @Operation(summary = "Get server local network IP and port for mobile QR code links")
    public ResponseEntity<Map<String, Object>> getNetworkInfo() {
        Map<String, Object> info = new HashMap<>();
        java.util.List<String> ips = discoverLanIps();
        String primaryIp = ips.isEmpty() ? "127.0.0.1" : ips.get(0);
        String hostName = getMachineHostName();
        
        info.put("serverPort", serverPort);
        info.put("lanIp", primaryIp);
        info.put("hostName", hostName);
        info.put("availableIps", ips);
        
        String effectiveHost = (configuredHost != null && !configuredHost.isBlank())
                ? configuredHost.trim()
                : primaryIp;
        
        String lanUrl = effectiveHost.startsWith("http://") || effectiveHost.startsWith("https://")
                ? effectiveHost
                : "http://" + effectiveHost + ":" + serverPort;
                
        info.put("lanUrl", lanUrl);
        info.put("hostnameUrl", "http://" + hostName + ":" + serverPort);
        info.put("mdnsUrl", "http://" + hostName.toLowerCase() + ".local:" + serverPort);
        return ResponseEntity.ok(info);
    }

    private String getMachineHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }

    private java.util.List<String> discoverLanIps() {
        java.util.List<String> list = new java.util.ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                
                String display = iface.getDisplayName().toLowerCase();
                // Exclude common virtual adapters like vEthernet, VirtualBox, VMware, WSL
                if (display.contains("virtual") || display.contains("vmware") || display.contains("vbox") || display.contains("wsl")) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (!ip.startsWith("169.254.") && !ip.startsWith("127.")) {
                            list.add(ip);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (list.isEmpty()) {
            try {
                String local = InetAddress.getLocalHost().getHostAddress();
                if (!local.startsWith("127.")) list.add(local);
            } catch (Exception ignored) {}
        }
        return list;
    }
}
