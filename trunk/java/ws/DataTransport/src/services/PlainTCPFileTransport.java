/*
   Copyright 2009 S. Koulouzis

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  
 */

package services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * This is a test class for a simpe TCP connection 
 * @author S. Koulouzis.
 */
public class PlainTCPFileTransport implements Runnable{
    
    private ServerSocket srvr;
    private int maxBufferSize;
    private BufferedOutputStream outStream;
    private BufferedInputStream inStream;
    private String file;
    private byte[] data;
    private Socket socket;
    private boolean server;
    
    /** Creates a new instance of PlainTCPFileTransport */
    public PlainTCPFileTransport(int buffSize,String file,boolean server,String host) {
        try {
            this.server = server;
            if(server){
                srvr = new ServerSocket(8199);
            }else{
                socket = new Socket(host,8199);
            }
//            maxBufferSize = buffSize;
            this.file = file;
            data = new byte[maxBufferSize];
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
    }
    
    
    public void run(){
        try {
            if(server){
                socket = srvr.accept();
                System.out.println("Connection!!!!!!");
                socket.setSendBufferSize(maxBufferSize);
                socket.getOutputStream();
                socket.setTcpNoDelay(false);
                outStream = new BufferedOutputStream(socket.getOutputStream(),maxBufferSize);
//                OutputStream out = socket.getOutputStream();
                
                FileInputStream fis = new FileInputStream(file);
                
                int len=0;
                
                while ((len = fis.read(data)) != -1) {
//                    outStream.write(data,0,len);
                    System.out.println("Len: "+len+" buffSize: "+maxBufferSize);
                    outStream.write(data,0,len);
                }
                fis.close();
                outStream.flush();
                outStream.close();
                socket.close();
                srvr.close();
            }else{
                socket.setReceiveBufferSize(maxBufferSize);
                inStream = new BufferedInputStream(socket.getInputStream(),maxBufferSize);
//                InputStream in = socket.getInputStream();
                int len=0;
                FileOutputStream fis = new FileOutputStream(file);
                while ((len = inStream.read(data)) != -1) {
//                 while ((len = in.read(data)) != -1) {
                    System.out.println("Len: "+len+" buffSize: "+maxBufferSize);
                    fis.write(data,0,len);
                }
                fis.flush();
                fis.close();
                inStream.close();
                socket.close();
            }
            
            
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
}
