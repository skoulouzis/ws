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

import com.lowagie.text.pdf.codec.Base64;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import org.apache.axis.transport.http.*;

import java.util.Iterator;

import java.rmi.RemoteException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.xml.namespace.QName;
import javax.xml.rpc.ServiceException;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.servlet.*;
import javax.servlet.http.*;

import javax.xml.rpc.holders.IntHolder;
import nl.uva.science.wsdtf.io.Connector;
import nl.uva.science.wsdtf.utilities.Initilize;
import nl.uva.science.wsdtf.utilities.StreamConfig;

import org.apache.axis.AxisEngine;
import org.apache.axis.AxisFault;
import org.apache.axis.MessageContext;
import org.apache.axis.attachments.AttachmentPart;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.client.async.AsyncCall;
import org.apache.axis.client.async.IAsyncResult;
import org.apache.axis.handlers.soap.SOAPService;
import org.apache.axis.description.*;
import org.apache.axis.providers.java.JavaProvider;

/**
 * <description> This is a WS that uses the streaming library and the Axis API for transfeing files </description>
 *
 * @author S. Koulouzis
 *
 */
public class DataTransportService {

    public static final int SWA = 100;
    public static final int SOAP = 200;
    public static final int HTTP = 300;
    public static final int GFTP = 400;
    public static final int TCP = 500;
    private Initilize init;
    private String tmpConfFilePath;
    private static long dataSize = 0;
    private static long totalSize = 0;
    /** logger for Commons logging. */
    private static transient Logger log =
            Logger.getLogger(DataTransportService.class.getName());

    /**
     * Creates a new instance of DataTransportService
     */
    public DataTransportService() {
    }

    /**
     * Use this method to send files.
     *@param source. The local directory, or file to send
     *@param endPoint. The WS location where to send the files
     *@param storeDir. The local directory to save the files. If the provided dir doesn't exist, files will be saved in Axis's attachment directory.
     *@param method. Method of sending files.     SWA=100,SOAP=200,HTTP=300,GFTP=400,TCP=500
     *@return The location of the files sent, in the destination WS.
     */
    public String sendFile(String source, String endPoint, String storeDir, int method) {
        String result = "NON";
        log.info("Sending " + source + " to " + endPoint);
        long start = System.currentTimeMillis();
        try {
            switch (method) {
                case SWA:
                    result = attachFiles(source, endPoint, storeDir);
                    break;

                case SOAP:
                    result = sendFilesOverSoap(source, endPoint, storeDir);
                    break;

                case HTTP:
                    result = sendFilesOverHttp(source, endPoint, storeDir);
                    break;

                case TCP:
                    result = sendFilesOverTCP(source, endPoint, storeDir);
                    break;

                default:
                    result = "Method of sending file is not known!!!";
                    break;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        long end = System.currentTimeMillis();
        long elapsed = end - start;
        log.info("\nTime elapsed: " + elapsed + " msec.\n" +
                "Data size: " + dataSize + " bytes \n" +
                "Size sent: " + totalSize + " bytes \n" +
                "Speed: " + (((dataSize / 1024.0) / ((elapsed) / 1000.0))) + " kbyte/sec");
        return result;
    }

    /**
     *Attaches files in a soap message, and invokes the WS specified in <code>endpoint</code>
     *@param source. The local file or folder
     *@param endpoint. The destination WS
     *@param storeDir. The remote directory to transfer files to.
     *@return the destinations WS response. Usually the location were the files are saved
     */
    private String attachFiles(String source, String endpoint, String storeDir) throws ServiceException, MalformedURLException, AxisFault, RemoteException {
        Service service = null;
        Call call = null;
        String result = null;
        service = new Service();
        call = (Call) service.createCall();
        call.setTargetEndpointAddress(new URL(endpoint + "DataTransportService"));
        call.setOperationName(new QName("getAttachments"));
        File dir = new File(source);
        dataSize = 0;
        if (dir.isDirectory()) {
            DataHandler[] dh = getAttachmentsFromDir(source);
            for (int i = 0; i < dh.length; i++) {
                dataSize = dataSize + dir.listFiles()[i].length();
                AttachmentPart ap = new AttachmentPart(dh[i]);
                ap.setContentId(dir.listFiles()[i].getName());
                call.addAttachmentPart(ap);
            }
        } else {
            dataSize = dataSize + dir.length();
            DataHandler dh = new DataHandler(new FileDataSource(source));
            
            AttachmentPart ap = new AttachmentPart(dh);
            ap.setContentId(dir.getName());
            call.addAttachmentPart(ap);
        }
        Object[] arg = {storeDir};
        result = (String) call.invoke(arg);

//        totalSize = getSOAPSize(call.getMessageContext().getRequestMessage());

        return result;
    }

    /**
     *Reads bytes from files encodes them to base64 and adds them in a soap message.
     * It also invokes the WS specified in <code>endpoint</code>. In order to prevent
     * soap from crashing the file is read and packed in a soap message in small chunks
     *@param source. The local file or folder.
     *@param endpoint. The destination WS.
     *@param dir. The remote directory to transfer files to
     *@return The destinations WS response. Usually the location were the files are saved
     */
    private String sendFilesOverSoap(String source, String endpoint, String dir) throws ServiceException, MalformedURLException, IOException {
        FileInputStream fileStream = null;
        File f = new File(source);
        File currentFile;
        Service service = null;
        Call call = null;
        String result = null;
        service = new Service();
        Vector files = new Vector();

        call = (Call) service.createCall();

        call.setTargetEndpointAddress(new URL(endpoint + "DataTransportService"));
        call.setOperationName(new QName("getFilesOverSoap"));


        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] tmp = new byte[500 * 1024];
        StringBuffer buffer = new StringBuffer();
        int len = 0;
        String data = "";
        if (f.isDirectory()) {
            for (int i = 0; i < f.listFiles().length; i++) {
                if (f.listFiles()[i].isFile()) {
                    files.add(f.listFiles()[i].getAbsolutePath());
                }
            }
        } else if (f.isFile()) {
            files.add(f.getAbsolutePath());
        }

        int s = 0;
        dataSize = 0;
        for (int i = 0; i < files.size(); i++) {
            currentFile = new File((String) files.get(i));
            fileStream = new FileInputStream(currentFile);
            dataSize = (dataSize + currentFile.length());

            while ((len = fileStream.read(tmp)) != -1) {
                baos.write(tmp, 0, len);
                data = Base64.encodeBytes(baos.toByteArray());
                s = s + baos.toByteArray().length;
                //make sure soap will not crach
                if (/*Runtime.getRuntime().freeMemory() < (Runtime.getRuntime().totalMemory()/4)*/s >= 15 * 1048576) {
                    Object[] args = {new String(currentFile.getName()), dir, buffer.toString().getBytes()};
                    result = (String) call.invoke(args);
                    totalSize = totalSize + getSOAPSize(call.getMessageContext().getRequestMessage());
                    buffer.setLength(0);
                    s = 0;
                } else {
                    buffer.append(data);
                }
                baos.reset();
            }
            if (buffer.toString().length() >= 1) {
                Object[] args = {new String(currentFile.getName()), dir, buffer.toString().getBytes()};
                result = (String) call.invoke(args);
                totalSize = totalSize + getSOAPSize(call.getMessageContext().getRequestMessage());
                buffer.setLength(0);
            }
        }

        return result;
    }

    /**
     *Copies files in to a http accessible folder, and invokes the destination WS, to open an http connection and get the files.
     *@param source. The local file or folder. If not the publish folder, files are copied to the publish folder.
     *@param endpoint. The destination WS.
     *@param dir. The remote directory to transfer files to
     *@return The destinations WS response. Usually the location were the files are saved
     */
    private String sendFilesOverHttp(String source, String endpoint, String dir) throws IOException, ServiceException {
        String locations = publishFile(source);
        String[] fileLocation = locations.split(",");
        Service service = null;
        Call call = null;
        service = new Service();
        File currentFile;
        call = (Call) service.createCall();

        call.setTargetEndpointAddress(new URL(endpoint + "DataTransportService"));
        call.setOperationName(new QName("getFileOverHttp"));
        locations = "";
        dataSize = 0;
        for (int i = 0; i < fileLocation.length; i++) {
            if (fileLocation[i].length() != 0) {
                currentFile = new File(fileLocation[i]);
                dataSize = dataSize + new File(getPublishDir() + "/" + currentFile.getName()).length();
                Object[] arg = {fileLocation[i], dir};
                locations = locations + "," + (String) call.invoke(arg);
            }
        }
        return locations;
    }

    /**
     *copies file(s) the the publish folder
     *@param source. The local file or folder.
     *@return the files copied. e.g. ,file1,file2,...,fileN
     */
    private String publishFile(String source) throws IOException {
        File file = new File(source);
        File dest;
        String files = "";
        int len;
        if (!file.exists()) {
            return source + " doesn't exist ";
        }
        if (file.isDirectory()) {
            if (!file.getAbsolutePath().equals(getPublishDir())) {
                for (int i = 0; i < file.listFiles().length; i++) {
                    dest = new File(getPublishDir() + "/" + file.listFiles()[i].getName());
                    if (file.listFiles()[i].isFile()) {
                        copy(file.listFiles()[i], dest);
                        files = files + "," + getPublishURL() + "/" + dest.getName();
                    }
                }
            } else {
                for (int i = 0; i < file.listFiles().length; i++) {
                    if (file.listFiles()[i].isFile() && file.listFiles()[i].exists()) {
                        files = files + "," + getPublishURL() + "/" + file.listFiles()[i].getName();
                    }
                }
            }
        }
        if (file.isFile() && file.getParent() != getPublishDir()) {
            dest = new File(getPublishDir() + "/" + file.getName());
            copy(file, dest);
            files = files + "," + getPublishURL() + "/" + dest.getName();
        }
        return files;
    }

    /**
     *Starts a streaming server using a TCP connection, and makes an asynchronous
     * call to the destination WS, to start is streaming client.
     *
     *@param source. The local file or folder.
     *@param endpoint. The destination WS.
     *@param dir. The remote directory to transfer files to.
     *@return The files sent. Since this is an asynchronous call this returns the files streamed.
     */
//    private String sendFilesOverTCP(String source,String endPoint,String storeDir) throws URISyntaxException{
//
//        URI server = new URI("tcp://"+getIP()+":8199");
//        dataSize = 0;
//        StreamConfig conf= getConf(server,"TCP");
//        File f = new File(source);
//        File currentfile;
//        Connector[] conn =  new Connector[1];
//        Vector files = new Vector();
//        String filesStored = "";
//        if(f.isDirectory()){
//            for(int i=0;i<f.listFiles().length;i++){
//                if(f.listFiles()[i].isFile()){
//                    files.add(f.listFiles()[i].getAbsolutePath());
//                }
//            }
//        }else if(f.isFile()){
//            files.add(f.getAbsolutePath());
//        }
//        conn = new Connector[1];
//
//        for(int i=0;i<files.size();i++){
//            long serStart = System.currentTimeMillis();
//            conn[0] = initTransfer(conf,0,true);
//            currentfile = new File((String)files.get(i));
//
//            while(!init.getThread("Server").getState().equals(Thread.State.RUNNABLE)){
//                if(init.getThread("Server").getState().equals(Thread.State.TERMINATED)){
//                    init.killServer();
//                    return "Problem Starting Server";
//                }
//            }
//            long serEnd = System.currentTimeMillis();
//            log.info("Streaming Server stated in "+(serEnd-serStart)+" msec");
//            Object[] arg = {init.Config2String(conf),storeDir,currentfile.getName(),conn.length};
//            asyncCall(arg,"getFileOverTCP",endPoint);
//            filesStored = filesStored +","+currentfile.getName();
//
//            conn[0].streamFile(currentfile.getAbsolutePath());
//            dataSize = dataSize + currentfile.length();
//            conn[0].END();
//            try {
//                init.getThread("Server").join();
//            } catch (InterruptedException ex) {
//                ex.printStackTrace();
//            }
//        }
////        new File(tmpConfFilePath).delete();
//        return filesStored;
//    }
    private String sendFilesOverTCP(String source, String endPoint, String storeDir) throws URISyntaxException {
        URI server = new URI("tcp://" + getIP() + ":8199");
        StreamConfig conf = getConf(server, "TCP");
        init = new Initilize();
        Thread ser;
        PlainTCPFileTransport s;
        dataSize = 0;

        File f = new File(source);
        File currentfile;

        Vector files = new Vector();
        String filesStored = "";
        if (f.isDirectory()) {
            for (int i = 0; i < f.listFiles().length; i++) {
                if (f.listFiles()[i].isFile()) {
                    files.add(f.listFiles()[i].getAbsolutePath());
                }
            }
        } else if (f.isFile()) {
            files.add(f.getAbsolutePath());
        }
        for (int i = 0; i < files.size(); i++) {
            long serStart = System.currentTimeMillis();
            currentfile = new File((String) files.get(i));
            s = new PlainTCPFileTransport(conf.maxBufferSize, currentfile.getAbsolutePath(), true, "");
            ser = new Thread(s);
            ser.start();
            while (!ser.getState().equals(Thread.State.RUNNABLE)) {
                if (ser.equals(Thread.State.TERMINATED)) {
                    return "Problem Starting Server";
                }
            }
            long serEnd = System.currentTimeMillis();
            log.info("Streaming Server stated in " + (serEnd - serStart) + " msec");
            Object[] arg = {init.Config2String(conf), storeDir, currentfile.getName(), 1};
            asyncCall(arg, "getFileOverTCP", endPoint);
            filesStored = filesStored + "," + currentfile.getName();
            dataSize = dataSize + currentfile.length();
            try {
                ser.join();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
        return filesStored;
    }

    /**
     * Recive files in a soap attachment
     *@param storeDir. The location to save the attachments.
     *@return String. The location of the files saved
     */
    public String getAttachments(String storeDir) throws SOAPException, FileNotFoundException, IOException {
        long start = System.currentTimeMillis();
        storeDir = getStoreDir(storeDir);
        log.info("Saving incoming files to " + storeDir);
        Iterator it = MessageContext.getCurrentContext().getCurrentMessage().getAttachments();
//        totalSize = getSOAPSize(MessageContext.getCurrentContext().getCurrentMessage());
        FileOutputStream myFOS = null;
        File myFile = null;
        String files = ",";

        while (it.hasNext()) {
            AttachmentPart ap = (AttachmentPart) it.next();
            DataHandler handler = ap.getDataHandler();
            myFile = new File(storeDir + "/" + ap.getContentId());
            myFOS = new FileOutputStream(myFile);
            handler.writeTo(myFOS);
            ap.dispose();
            files = files + "," + myFile.getAbsolutePath();
            dataSize = dataSize + myFile.length();
        }
        myFOS.flush();
        myFOS.close();
        long end = System.currentTimeMillis();
        long elapsed = end - start;
        log.info("Time elapsed:" + elapsed + " msec \n" +
                "Data recived: " + dataSize + " bytes \n" +
                "Total size: " + totalSize + " bytes \n" +
                "Speed: " + (((dataSize / 1024.0) / ((elapsed) / 1000.0))) + " kbyte/sec");
        return files;
    }

    /**
     *Get the file bytes in a soap message. This method will append incoming bytes if the file exists, otherwise it will crate a new
     *@param fileName. The file name
     *@param storeDir. The local directory that files will be saved.
     *@param byte[]. The files bytes. They have to be encoded in base64.
     *@return the file names saved.
     */
    public String getFilesOverSoap(String fileName, String storeDir, byte[] base64Data) throws IOException {
        long start = System.currentTimeMillis();
        storeDir = getStoreDir(storeDir);
        log.info("Saving " + fileName + " to " + storeDir);
        FileOutputStream fstream;
        File file = new File(storeDir + "/" + fileName);
        String data = new String(base64Data);
        fstream = new FileOutputStream(file, file.exists());
        byte[] byteData = Base64.decode(data);
        fstream.write(byteData);
        dataSize = dataSize + file.length();
        totalSize = totalSize + getSOAPSize(MessageContext.getCurrentContext().getCurrentMessage());
        long end = System.currentTimeMillis();
        long elapsed = end - start;
        log.info("Time elapsed:" + elapsed + " msec \n" +
                "Data recived: " + dataSize + " bytes \n" +
                "Total size: " + totalSize + " bytes \n" +
                "Speed: " + (((dataSize / 1024.0) / ((elapsed) / 1000.0))) + " kbyte/sec");
        return file.getAbsolutePath();
    }

    /**
     *Get a file from a a url location.
     *@param fileUrl. The url location of the file.
     *@param storeDir. The local directory that files will be saved.
     *@return the location of the saved files
     */
    public String getFileOverHttp(String fileUrl, String storeDir) {
        long start = System.currentTimeMillis();
        URI source = null;
        String dest = null;
        File currentFile;
        log.info("Saving " + fileUrl + " to " + storeDir);
        try {
            storeDir = getStoreDir(storeDir);
            source = new URI(fileUrl);
            StreamConfig conf = getConf(source, "HTTP");
            Connector conn = initTransfer(conf, 0, false);

            String[] fileName = source.toURL().getFile().split("/");

            conn.save(storeDir + "/" + fileName[fileName.length - 1]);
            dest = storeDir + "/" + fileName[fileName.length - 1];
            currentFile = new File(dest);
            dataSize = dataSize + currentFile.length();
        } catch (MalformedURLException ex) {
            //is it the conf in XML?
//            createTemporaryFile(fileUrl,"xml");
            ex.printStackTrace();
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
        }
        long end = System.currentTimeMillis();
        long elapsed = end - start;
        log.info("Time elapsed:" + elapsed + " msec \n" +
                "Data recived: " + dataSize + " bytes \n" +
                "Speed: " + (((dataSize / 1024.0) / ((elapsed) / 1000.0))) + " kbyte/sec");
        return dest;
    }

    /**
     *Connects with the streaming library, and saves incoming bytes to a file. Note for every file that is saved, a new connection is made.
     *@param conf. The stream client configuration
     *@param storeDir. The store directory
     *@@param fileName. The file name
     *@int connections. The number of concurrent connections (not tested. best use one).
     *@return the files saved
     */
//    public String getFileOverTCP(String conf,String storeDir,String fileName,int connections){
//        long start = System.currentTimeMillis();
//        storeDir = getStoreDir(storeDir);
//        log.info("Saving "+fileName+" to "+storeDir);
//        String fileNames = "";
//        File currentFile;
//        init = new Initilize();
//        StreamConfig config = init.String2Config(conf);
//        Connector[] conn = new Connector[connections];
//        String files ="";
//        for(int i=0;i<conn.length;i++){
//            conn[i] = initTransfer(config,i,false);
//            files = storeDir+"/"+i+"-"+fileName;
//            fileNames = fileNames+","+files;
//            conn[i].save(files);
//            currentFile = new File(files);
//            dataSize = dataSize + currentFile.length();
//        }
//        long end = System.currentTimeMillis();
//        long elapsed = end - start;
//        log.info("Time elapsed:"+ elapsed +" msec \n"+
//                "Data recived: "+dataSize+" bytes \n"+
//                "Speed: "+( ( (dataSize/1024.0)/((elapsed)/1000.0)) )+" kbyte/sec" );
//        return fileNames;
//    }
    public String getFileOverTCP(String conf, String storeDir, String fileName, int connections) {
        long start = System.currentTimeMillis();
        storeDir = getStoreDir(storeDir);
        log.info("Saving " + fileName + " to " + storeDir);
        String fileNames = "";
        File currentFile;
        init = new Initilize();
        StreamConfig config = init.String2Config(conf);
        Thread cli;
        PlainTCPFileTransport s;
        String files = "";
        for (int i = 0; i < 1; i++) {

            files = storeDir + "/" + i + "-" + fileName;
            fileNames = fileNames + "," + files;
            s = new PlainTCPFileTransport(config.maxBufferSize, files, false, config.host);
            cli = new Thread(s);
            currentFile = new File(files);
            dataSize = dataSize + currentFile.length();
        }
        long end = System.currentTimeMillis();
        long elapsed = end - start;
        log.info("Time elapsed:" + elapsed + " msec \n" +
                "Data recived: " + dataSize + " bytes \n" +
                "Speed: " + (((dataSize / 1024.0) / ((elapsed) / 1000.0))) + " kbyte/sec");
        return fileNames;
    }

    /**
     *Returns files in a folder as data handler array, so it may attached in a soap message.
     *@param dirName. the folder.
     *@return the data handler
     */
    private DataHandler[] getAttachmentsFromDir(String dirName) {
        java.util.LinkedList retList = new java.util.LinkedList();
        DataHandler[] ret = new DataHandler[0];// empty

        java.io.File sourceDir = new java.io.File(dirName);

        java.io.File[] files = sourceDir.listFiles();

        for (int i = files.length - 1; i >= 0; --i) {
            java.io.File cf = files[i];

            if (cf.isFile() && cf.canRead() && !cf.getName().equals(".")) {
                String fname = null;

                try {
                    fname = cf.getAbsoluteFile().getCanonicalPath();
                } catch (java.io.IOException e) {
                    System.err.println("Couldn't get file \"" + fname + "\" skipping...");
                    continue;
                }
                retList.add(new DataHandler(new FileDataSource(fname)));
                
                
            }
        }
        if (!retList.isEmpty()) {
            ret = new DataHandler[retList.size()];
            ret = (DataHandler[]) retList.toArray(ret);
        }

        return ret;
    }

//    private void displayMessage(SOAPMessage message) throws Exception {
//        TransformerFactory tFact = TransformerFactory.newInstance();
//        Transformer transformer = tFact.newTransformer();
//        Source src = message.getSOAPPart().getContent();
//        StreamResult result = new StreamResult( System.out );
//        transformer.transform(src, result);
//    }
    /**
     *Get a soap's message size in bytes .
     *@param message. The soap message
     *@return the size.
     */
    private int getSOAPSize(SOAPMessage message) {
        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();
            message.writeTo(baos);
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (SOAPException ex) {
            ex.printStackTrace();
        }
        return baos.toByteArray().length;
    }

    /**
     *Gets the folder where the incoming files are saved. If the folder doesn't exists, it returns the axis attachment folder.
     *@param dir. The folder.
     *@return the same folder, or the axis attachment folder.
     */
    private String getStoreDir(String dir) {
        File fdir = new File(dir);
        if (!fdir.exists() || !fdir.isDirectory() || !fdir.canWrite()) {
            dir = (String) AxisEngine.getCurrentMessageContext().getProperty(AxisEngine.PROP_ATTACHMENT_DIR);
        }
        return dir;
    }

    /**
     *Deletes a local file.
     *@param file. The file
     *@return true on success.
     */
    public boolean deleteFile(String file) {
        File f = new File(file);
        return f.delete();
    }

    /**
     *Copies a file.
     *@param src. The source.
     *@param dst. The destination
     */
    private void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        byte[] buf = new byte[2048];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    /**
     *ls.
     *
     *@param strDir. The folder.
     *@return files in that folder
     */
    public String ls(String strDir) {
        String res = "";
        File dir = new File(strDir);
        if (dir.isDirectory()) {
            for (int i = 0; i < dir.listFiles().length; i++) {
                res = res + "," + dir.getName();
            }
        } else {
            res = dir.getName();
        }
        return res;
    }

    /**
     *Returns the url location of the published files.
     *@return The url location
     */
    public String getPublishURL() throws UnknownHostException {
        URL addr = null;
        try {
            addr = new URL((String) AxisEngine.getCurrentMessageContext().getProperty("transport.url"));
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
        return "http://" + addr.getAuthority() + "/axis/" + new File(getPublishDir()).getName();
    }

    /**
     *Returns the location of the published files in the local disk
     *@return The location
     */
    public String getPublishDir() {
//        File dir = new File((String)AxisEngine.getCurrentMessageContext().getProperty("publish.Directory"));
        File dir = new File((String) System.getProperty("catalina.home") + "/webapps/axis/publishFiles");
        if (!dir.exists()) {
            dir.mkdir();
        }
//        return (String)AxisEngine.getCurrentMessageContext().getProperty("publish.Directory");
        return dir.getAbsolutePath();
    }

    /**
     *Initializes the streaming library and return a <code>Connector</code>
     *@param conf. The <code>StreamConfig</code>
     *@param inc. The next connection number, so if using concurrent connections, the port number will increase.
     *@param server. True if server.
     */
    private Connector initTransfer(StreamConfig conf, int inc, boolean server) {
        Connector conn = null;
        try {
            init = new Initilize(conf);
            init.setPort(init.getPort() + inc);
            if (server) {
                init.server();
            } else {
                init.client();
            }
            conn = init.getConnector();
            if (server) {
                init.startServer();
            } else {
                init.startClient();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return conn;
    }

    /**
     *Initializes the streaming library and return a <code>Connector</code>
     *@param confiFile. The location of a config file.
     *@param inc. The next connection number, so if using concurrent connections, the port number will increase.
     *@param server. True if server.
     */
    private Connector initTransfer(String confiFile, int inc, boolean server) {
        Connector conn = null;
        try {
            init = new Initilize(confiFile);
            init.setPort(init.getPort() + inc);
            if (server) {
                init.server();
            } else {
                init.client();
            }
            conn = init.getConnector();
            if (server) {
                init.startServer();
            } else {
                init.startClient();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return conn;
    }

    /**
     *Returns a <code>StreamConfig</code>
     *@TO-DO remove the protocol param
     *@param source. The server's location protocol folder or file and port e.g. tcp://some.location.com:8199/tmp/file.dat or swa://some.location.com:8199/tmp/
     *@param the protocol to initialize
     *@return the configuration
     */
    private StreamConfig getConf(URI source, String protocol) {
        StreamConfig conf = new StreamConfig();
        conf.displaySpeed = false;
        conf.protocol = protocol;

        if (protocol.equals("HTTP")) {
            conf.fileRequest = source.getPath();
            conf.host = "http://" + source.getHost();
            conf.port = source.getPort();
        } else {
            conf.host = source.getHost();
            conf.port = 8199;
        }

        conf.loggLevel = "WARNING";
//        conf.maxBufferSize = 5242880;
        conf.maxBufferSize = 8388608;
//        conf.maxBufferSize = 1024*65;
        conf.ecnryptData = false;
        conf.authenticate = false;
        conf.displaySpeed = false;
        conf.saveLogs = false;

        return conf;
    }

//    public String getTCPServerEndPoint(String path){
//        File f = new File(path);
//        if(!f.exists()){
//            path = System.getProperty("java.io.tmpdir")+"/tmp.xml";
//        }
//        f = new File( path );
//        int cnt=0;
//        while(!f.exists()){
//            try {
//                Thread.sleep(100);
//                cnt++;
//                if(cnt>=100){
//                    break;
//                }
//            } catch (InterruptedException ex) {
//                ex.printStackTrace();
//            }
//        }
//
//        return readFileAsString(path);
//    }
    private static String createTemporaryFile(String text, String extension) {

        int cnt = 0;

        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp == null || tmp.equalsIgnoreCase("")) {
            tmp = ".";
        }

//        String file = tmp + File.separator + "tmp." + cnt + "." + extension;
        String file = tmp + File.separator + "tmp" + "." + extension;

//        while (new File(file).exists()) {
//            cnt++;
//            file = tmp + File.separator + "tmp." + cnt + "." + extension;
//        }

        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(file, true));
            bw.write(text);
            bw.newLine();
            bw.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {                       // always close the file
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException ioe2) {
                    ioe2.printStackTrace();
                }
            }
        }

        return file;

    }

    private String getIP() {
        URL addr = null;
        try {
            addr = new URL((String) AxisEngine.getCurrentMessageContext().getProperty("transport.url"));
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
        return addr.getHost();
//        InetAddress addr=null;
//        String strAddr=null;
//        try {
//            addr = InetAddress.getLocalHost();
//
//            strAddr = addr.getHostName();
//        } catch (UnknownHostException ex) {
//            ex.printStackTrace();
//        }
//        byte[] ipAddr = addr.getAddress();
//        String ipAddrStr = "";
//        for (int i=0; i<ipAddr.length; i++) {
//            if (i > 0) {
//                ipAddrStr += ".";
//            }
//            ipAddrStr += ipAddr[i]&0xFF;
//        }
//        return ipAddrStr;
    }

    private static String readFileAsString(String filePath) {
        StringBuffer fileData = new StringBuffer(1000);
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            char[] buf = new char[1024];
            int numRead = 0;
            while ((numRead = reader.read(buf)) != -1) {
                String readData = String.valueOf(buf, 0, numRead);
                fileData.append(readData);
                buf = new char[1024];
            }
            reader.close();
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return fileData.toString();
    }

    private String asyncCall(Object[] args, String method, String endpoint) {
        Service service = null;
        Call call = null;
        AsyncCall aCall = null;
        String result = null;
        IAsyncResult asyncResult = null;
        try {
            service = new Service();
            call = (Call) service.createCall();
            call.setTargetEndpointAddress(new URL(endpoint + "DataTransportService"));
            call.setOperationName(new QName(method));

            aCall = new AsyncCall(call);

            asyncResult = aCall.invoke(args);
        } catch (ServiceException ex) {
            ex.printStackTrace();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
        return (String) asyncResult.getResponse();
    }

    private String call(Object[] args, String method, String endpoint) {
        Service service = null;
        Call call = null;
        String result = null;
        try {
            service = new Service();
            call = (Call) service.createCall();
            call.setTargetEndpointAddress(new URL(endpoint + "DataTransportService"));
            call.setOperationName(new QName(method));
            result = (String) call.invoke(args);
        } catch (ServiceException ex) {
            ex.printStackTrace();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
        return result;
    }

    /**
     * Zips a local folder (not tested)
     *@param folderPath. The local folder
     *@param filePath. The ziped folder location
     *@return boolean. On success true
     */
    public boolean zipFolder(String folderPath, String filePath) {
        File outFile = null;
        try {
            outFile = new File(filePath);
            File inFolder = new File(folderPath);
            //compress outfile stream
            ZipOutputStream out = new ZipOutputStream(
                    new BufferedOutputStream(
                    new FileOutputStream(outFile)));

            //writting stream
            BufferedInputStream in = null;

            byte[] data = new byte[10 * 1024];
            String files[] = inFolder.list();

            for (int i = 0; i < files.length; i++) {
                //System.out.println("Adding: " + files[i]);
                in = new BufferedInputStream(new FileInputStream(inFolder.getPath() + "/" + files[i]), 10 * 1024);

                out.putNextEntry(new ZipEntry(files[i])); //write data header (name, size, etc)
                int count;
                while ((count = in.read(data)) != -1) {
                    out.write(data, 0, count);
                }
                out.closeEntry(); //close each entry
            }
            out.flush();
            out.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Unzips a local folder (not tested)
     *@param filePath. The file to unzip
     *@param folderPath. The local folder to extract
     *@return boolean. On success true
     */
    public boolean unzip(String filePath, String folderPath) {
        try {
            File inFile = new File(filePath);
            File outFolder = new File(folderPath);
            BufferedOutputStream out = null;

            ZipInputStream in = new ZipInputStream(new BufferedInputStream(
                    new FileInputStream(inFile)));
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                int count;

                byte data[] = new byte[10 * 1024];
                // write the files to the disk
                out = new BufferedOutputStream(new FileOutputStream(outFolder.getPath() + "/" + entry.getName()), 10 * 1024);
                while ((count = in.read(data)) != -1) {
                    out.write(data, 0, count);
                }
                out.flush();
                out.close();
            }
            in.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String listTomcatApplets(String st) {
        String info = "Info:";
        try {
            HttpServlet srv = (HttpServlet) MessageContext.getCurrentContext().getProperty(HTTPConstants.MC_HTTP_SERVLET);
            ServletContext context = srv.getServletContext();

            FileInputStream fis = new FileInputStream("/home/alogo/testTrans/file1");
            context.setAttribute("file.in", fis);

            info = "http://localhost:8080/axis/ReverseServlet1";

        } catch (Exception ex) {
            Logger.getLogger(DataTransportService.class.getName()).log(Level.SEVERE, null, ex);
        }
        return info;
    }

    public String proxyCall(long arg) {
        String info = "Info:";
        try {
//            HttpServlet srv = (HttpServlet) MessageContext.getCurrentContext().getProperty(HTTPConstants.MC_HTTP_SERVLET);

//            Iterator iter = MessageContext.getCurrentContext().getAllPropertyNames();
//            
//            while (iter.hasNext()){
//                info = info + "\n"+(String)iter.next();
//            }
            MessageContext msgContx = MessageContext.getCurrentContext();
            AxisEngine engine = msgContx.getAxisEngine();
            SOAPService service = (SOAPService) engine.getService("SimleService");

            info = info + "\ngetName " + service.getName();
            info = info + "\ngetServiceDescription().getEndpointURL " + service.getServiceDescription().getEndpointURL();
            info = info + "\ngetServiceDescription().getName() " + service.getServiceDescription().getName();
            info = info + "\ngetClass().getName() " + service.getClass().getName();
            info = info + "\nisRunning " + new Boolean(service.isRunning()).toString();

            Iterator iter = service.getServiceDescription().getOperations().iterator();
            org.apache.axis.description.OperationDesc desc;
            while (iter.hasNext()) {
                desc = (org.apache.axis.description.OperationDesc) iter.next();
                info = info + "\n desc.getName(): " + desc.getName();
                info = info + "\ndesc.getMethod().getDeclaringClass().getName(): " + desc.getMethod().getDeclaringClass().getName();
                info = info + "\ndesc.getClass().getName(): " + desc.getClass().getName();
            }
            Object obj = getServiceObject("SimleService", MessageContext.getCurrentContext());
            info = info + "\nobj.getClass().getName()" + obj.getClass().getName();


            ServiceDesc initService = service.getInitializedServiceDesc(msgContx);
            info = info + "\n initService.getName(): " + initService.getName();
//             initService.getProperty(ServiceDescUtil.WSA_ACTION_OUTPUT_MAP);
//                info = info + "\ninitService.getMethod().getDeclaringClass().getName(): " + initService.getProperty(info);
            info = info + "\ninitService.getClass().getName(): " + initService.getClass().getName();

//            desc = service.getServiceDescription().getOperationByName("factor");
//            info = info + "\n Resalut from DataTrans:";
//            info = info + (String) desc.getMethod().getClass().getName();


        // get the service object corresponding to the service name           


//            java.lang.reflect.Method method = this.getMethod(MessageContext.getCurrentContext(), "SimleService", "factor");
//            method.invoke(serviceObject, arg1)
//            ServletContext context = srv.getServletContext();
//
//            FileInputStream fis = new FileInputStream("/home/alogo/testTrans/file1");
//            context.setAttribute("file.in", fis);
//
//            info = "http://localhost:8080/axis/ReverseServlet1";

        } catch (Exception ex) {
            Logger.getLogger(DataTransportService.class.getName()).log(Level.SEVERE, null, ex);
        }
        return info;
    }

    /**
     * Get a method of the service. Check first in the methodCache, whether
     * it is cached there before creating it via reflection
     *
     * @param msgCtx MessageContext
     * @param serviceName Name of the service that will be called
     * @param methodName Name of the method that will be called
     */
    private synchronized Method getMethod(MessageContext msgCtx, String serviceName, String methodName) throws Exception {
        Method method = null;
        String name = serviceName + "." + methodName;
//        if (methodCache.containsKey(name)) {
//            method = (Method) methodCache.get(name);
//        } else {
        method = msgCtx.getService().getServiceDescription().getOperationByName(methodName).getMethod();
//            methodCache.put(name, method);
//        }
        return method;
    }

    /**
     * Get the service object. Class name and Provider are looked up in
     * the deployment descriptor which is passed as argument.
     * Once a service object has been created it's cached for further use
     *
     * @wsddFileName Name of the deployment descriptor file of the service
     * @serviceName Name of the service to be called
     * @msgCtx MessageContext of the current thread
     */
    private synchronized Object getServiceObject(String serviceName, MessageContext msgCtx) throws Exception {
        Object serviceObject = null;
        // check if ServiceObject is cached
        SOAPService service = (SOAPService) msgCtx.getCurrentContext().getAxisEngine().getService(serviceName);

        String serviceClassName = service.getServiceDescription().getOperationByName(serviceName).getMethod().getDeclaringClass().getName();
//            String providerClassName = (String) ContextUtils.getServiceProperty(msgCtx,serviceName,"handlerClass");

        // create a Provider object
        Class providerClass = Class.forName(serviceClassName);
        Constructor co = providerClass.getConstructor(null);
        JavaProvider provider = (JavaProvider) co.newInstance(null);

        // get the service object
        serviceObject = provider.getServiceObject(msgCtx, msgCtx.getService(), serviceClassName, new IntHolder());

        // cache service object

        return serviceObject;
    }

    /**
     * Call a method of a service locally without doing a WS call
     *
     * @param serviceName Name of the service that will be called
     * @param wsddFileName Name of the deployment descriptor file,
    relative to $GLOBUS_LOCATION
     * @param methodName Name of the method that will be called
     * @param userSubject Subject of the user who does the call
     * @param args Arguments passed to the method that will be called
     * @param resourceKey key of the resource, the method will work on
     *
     */
    public Object callService(
            String serviceName,
            String methodName,
            String userSubject,
            Object[] args)
            throws Exception {

        // create MessageContext
        MessageContext msgCtx = MessageContext.getCurrentContext();

        // get the service object corresponding to the service name
        Object serviceObject = getServiceObject(
                serviceName,
                msgCtx);

        // get the method that will be called
        Method method = getMethod(
                msgCtx,
                serviceName,
                methodName);

        // associate the MessageContext to the current thread
//        MessageContextHelper msgCtxHelper = new MessageContextHelper(msgCtx);
//        msgCtxHelper.set();

        // do the local call
        Object returnValue = null;
        try {
            returnValue = method.invoke(serviceObject, args);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        } finally {
            // de-associate the MessageContext from the current thread
//            msgCtxHelper.unset();
        }

        return returnValue;
    }

}
