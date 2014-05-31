/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 * THE FILE HAS BEEN AUTOGENERATED BY MSGTOOL TOOL.
 * All changes made to this file manually will be overwritten 
 * if this tool runs again. Better make changes in the template file.
 */

package org.apache.harmony.luni.internal.nls;


/**
 * This class retrieves strings from a resource bundle and returns them,
 * formatting them with MessageFormat when required.
 * <p>
 * It is used by the system classes to provide national language support, by
 * looking up messages in the <code>
 *    org.apache.harmony.luni.internal.nls.messages
 * </code>
 * resource bundle. Note that if this file is not available, or an invalid key
 * is looked up, or resource bundle support is not available, the key itself
 * will be returned as the associated message. This means that the <em>KEY</em>
 * should a reasonable human-readable (english) string.
 * 
 * JJV: Cut down for Javascript conversion
 */
@sc.js.JSSettings(prefixAlias="ai_")
public class Messages {
    /**
     * Retrieves a message which has no arguments.
     * 
     * @param msg
     *            String the key to look up.
     * @return String the message for that key in the system message bundle.
     */
    static public String getString(String msg) {
       return msg;
    }

    /**
     * Retrieves a message which takes 1 argument.
     * 
     * @param msg
     *            String the key to look up.
     * @param arg
     *            Object the object to insert in the formatted output.
     * @return String the message for that key in the system message bundle.
     */
    static public String getString(String msg, int arg) {
        return msg + ": " + arg;
    }


    /**
     * Retrieves a message which takes several arguments.
     * 
     * @param msg
     *            String the key to look up.
     * @param args
     *            Object[] the objects to insert in the formatted output.
     * @return String the message for that key in the system message bundle.
     */
    static public String getString(String msg, Object... args) {
       return msg;
    }
}
