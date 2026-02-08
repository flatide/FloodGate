/*
 * MIT License
 *
 * Copyright (c) 2022 FLATIDE LC.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.flatide.floodgate.system.datasource

import com.fasterxml.jackson.databind.ObjectMapper
import com.flatide.floodgate.ConfigurationManager
import com.flatide.floodgate.FloodgateConstants

import org.apache.logging.log4j.LogManager

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class FDataSourceFile(name: String) : FDataSourceDefault(name) {
    // NOTE spring boot의 logback을 사용하려면 LogFactory를 사용해야 하나, 이 경우 log4j 1.x와 충돌함(SoapUI가 사용)
    companion object {
        private val logger = LogManager.getLogger(FDataSourceFile::class.java)
    }

    var path: String = "."

    var type: String? = ConfigurationManager.getString("datasource.$name.format")

    //HashMap<String, String> filenameRule = new HashMap<>();

    /*public MetaSourceFile(String path, String type) {
        this.path = path;
        this.type = type;
    }*/

    override fun connect(): Boolean {
        val path = File(this.path)
        return path.exists()
    }

    override fun getAllKeys(tableName: String, keyColumn: String): List<String> {
        val result = ArrayList<String>()

        val filename = makeFilename(tableName, ".+")    // <ID>가 있다면 .+으로 대체

        if (isMultipleFile(tableName)) {
            val p = filename.substring(0, filename.lastIndexOf("/"))
            val rule = filename.substring(filename.lastIndexOf("/") + 1)
            val f = File(this.path + "/" + p)

            val filter = java.io.FilenameFilter { _, name ->
                val pattern = Pattern.compile(rule)
                val matcher = pattern.matcher(name)
                if (matcher.matches()) {
                    logger.debug("$name : ${matcher.matches()}")
                }
                matcher.matches()
            }

            val files = f.list(filter)

            if (files != null) {
                val prefixLength = rule.indexOf(".+")
                val postfixLength = rule.length - (prefixLength + 2)
                for (file in files) {
                    var f2 = file.substring(prefixLength)
                    f2 = f2.substring(0, f2.length - postfixLength)
                    result.add(f2)
                }
            }
        } else {
            val data = readJson(this.path + "/" + filename)

            for (key in data.keys) {
                result.add(key)
            }
        }

        return result
    }

    override fun create(key: String): Boolean {
        return false
    }

    /*
    @Override
    public Map<String, Object> readData(String tableName, String keyColumn, String key) throws Exception {
        String filename = this.path + "/" + makeFilename(tableName, key);
        Map<String, Object> data = readJson(filename);

        if( isMultipleFile(tableName) ) {
            return data;
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) data.get(key);
            return row;
        }
    }

    @Override
    public boolean insertData(String tableName, String keyColumn, String key, Map<String, Object> row) throws Exception {
        String filename = this.path + "/" + makeFilename(tableName, key);
        if( isMultipleFile(tableName)) {
            File file = new File(filename);
            if( file.exists() ) {
                return false;   // key is duplicated
            }

            writeJson(filename, row);
        } else {
            Map<String, Object> data;
            File file = new File(filename);
            if( !file.exists()) {
                data = new HashMap<>();
            } else {
                data = readJson(filename);
                if( data == null ) {
                    data = new HashMap<>();
                }
            }
            if( data.get(key) == null ) {
                data.put(key, row);
                writeJson(filename, data);
            } else {
                return false;   // key is duplicated
            }
        }

        return true;
    }

    @Override
    public boolean updateData(String tableName, String keyColumn, String key, Map<String, Object> row) throws Exception {
        String filename = this.path + "/" + makeFilename(tableName, key);
        if( isMultipleFile(tableName) ) {
            File file = new File(filename);
            if( !file.exists()) {
                return false;   // key is not exist
            }

            writeJson(filename, row);
        } else {
            File file = new File(filename);
            if( !file.exists()) {
                return false;   // key is not exist
            }

            Map<String, Object> data = readJson(filename);
            if( data == null || data.get(key) == null) {
                return false;   // key is not exist
            }

            data.put(key, row);
            writeJson(filename, data);
        }

        return true;
    }

    @Override
    public boolean deleteData(String tableName, String keyColumn, String key, boolean backup) throws Exception {
        String filename = this.path + "/" + makeFilename(tableName, key);

//        File folder = new File(this.path + "/" + config.getSource().get("backupFolder"));
//        if( folder.exists() == false ) {
//            folder.mkdir();
//        }

        String backupFilename = this.path + "/" + ConfigurationManager.getConfig().getMeta().get("source.backupFolder") + "/" + makeBackupFilename(tableName, key);
        File backupFile = new File(backupFilename);
        if( backupFile.exists()) {
            backupFilename += "." + UUID.randomUUID();
        }

        if (isMultipleFile(tableName)) {
            File file = new File(filename);
            if( !file.exists()) {
                return false;
            }

            Map<String, Object> data = readJson(filename);
            if( data == null ) {
                return false;   // key is not exist
            }

            if( !file.delete() ) {
                throw new IOException("Cannot delete file " + filename);
            }

            if( backup ) {
                //file.renameTo(new File(backupFilename));
                writeJson(backupFilename, data);
            }
        } else {
            File file = new File(filename);
            if( !file.exists()) {
                return false;   // key is not exist
            }

            Map<String, Object> data = readJson(filename);
            if( data == null || data.get(key) == null) {
                return false;   // key is not exist
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) data.get(key);
            if( backup ) {
                Map<String, Object> backupData = new HashMap<>();
                backupData.put(key, row);

                writeJson(backupFilename, backupData);
            }
            data.remove(key);
            writeJson(filename, data);
        }

        return true;
    }*/

    @Suppress("UNCHECKED_CAST")
    override fun read(tableName: String, keyColumn: String, key: String): Map<String, Any> {
        val filename = this.path + "/" + makeFilename(tableName, key)
        val data = readJson(filename)

        return if (isMultipleFile(tableName)) {
            data
        } else {
            data[key] as Map<String, Any>
        }
    }

    override fun insert(tableName: String, keyColumn: String, row: Map<String, Any>): Boolean {
        val mutableRow = row.toMutableMap()
        val key = mutableRow.remove(keyColumn) as String
        val filename = this.path + "/" + makeFilename(tableName, key)
        if (isMultipleFile(tableName)) {
            val file = File(filename)
            if (file.exists()) {
                return false   // key is duplicated
            }

            writeJson(filename, mutableRow)
        } else {
            var data: MutableMap<String, Any>
            val file = File(filename)
            if (!file.exists()) {
                data = HashMap()
            } else {
                data = readJson(filename).toMutableMap()
            }
            if (data[key] == null) {
                data[key] = mutableRow
                writeJson(filename, data)
            } else {
                return false   // key is duplicated
            }
        }

        return true
    }

    override fun update(tableName: String, keyColumn: String, row: Map<String, Any>): Boolean {
        val mutableRow = row.toMutableMap()
        val key = mutableRow.remove(keyColumn) as String
        val filename = this.path + "/" + makeFilename(tableName, key)
        if (isMultipleFile(tableName)) {
            val file = File(filename)
            if (!file.exists()) {
                return false   // key is not exist
            }

            writeJson(filename, mutableRow)
        } else {
            val file = File(filename)
            if (!file.exists()) {
                return false   // key is not exist
            }

            val data = readJson(filename).toMutableMap()
            if (data[key] == null) {
                return false   // key is not exist
            }

            data[key] = mutableRow
            writeJson(filename, data)
        }

        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun delete(tableName: String, keyColumn: String, key: String): Boolean {
        val filename = this.path + "/" + makeFilename(tableName, key)

//        File folder = new File(this.path + "/" + config.getSource().get("backupFolder"));
//        if( folder.exists() == false ) {
//            folder.mkdir();
//        }

        var backupFilename = this.path + "/" + ConfigurationManager.getString(FloodgateConstants.META_SOURCE_BACKUP_FOLDER) + "/" + makeBackupFilename(tableName, key)
        val backupFile = File(backupFilename)
        if (backupFile.exists()) {
            backupFilename += "." + UUID.randomUUID()
        }

        if (isMultipleFile(tableName)) {
            val file = File(filename)
            if (!file.exists()) {
                return false
            }

            val data = readJson(filename)

            if (!file.delete()) {
                throw IOException("Cannot delete file $filename")
            }
        } else {
            val file = File(filename)
            if (!file.exists()) {
                return false   // key is not exist
            }

            val data = readJson(filename).toMutableMap()
            if (data[key] == null) {
                return false   // key is not exist
            }

            val row = data[key] as Map<String, Any>
            data.remove(key)
            writeJson(filename, data)
        }

        return true
    }

    override fun deleteAll(): Int {
        return 0
    }

    override fun flush() {
    }

    override fun close() {
    }

    fun getRule(tableName: String): String? {
        return ConfigurationManager.getString("datasource.${this.name}.filename.$tableName")
    }

    fun getBackupRule(tableName: String): String {
        var rule = ""
        if (tableName == ConfigurationManager.getString(FloodgateConstants.META_SOURCE_TABLE_FOR_FLOW)) {
            rule = ConfigurationManager.getString(FloodgateConstants.META_SOURCE_BACKUP_RULE_FOR_FLOW) ?: ""
        } else if (tableName == ConfigurationManager.getString(FloodgateConstants.META_SOURCE_TABLE_FOR_DATASOURCE)) {
            rule = ConfigurationManager.getString(FloodgateConstants.META_SOURCE_BACKUP_RULE_FOR_DATASOURCE) ?: ""
        }

        return rule
    }

    fun makeFilename(tableName: String, key: String): String {
        var rule = getRule(tableName)

        if (rule == null || rule.isEmpty()) {
            rule = "$tableName.json"
        } else {
            rule = rule.replace("<TABLE>".toRegex(), tableName)
            rule = rule.replace("<ID>".toRegex(), key)
        }

        return rule
    }

    fun makeBackupFilename(tableName: String, key: String): String {
        var rule = getBackupRule(tableName)

        val dateFormat = "yyyy_MM_dd_HH_mm_ss"
        val format = SimpleDateFormat(dateFormat)

        val current = System.currentTimeMillis()
        val date = Date(current)

        val currentDateTime = format.format(date)

        if (rule.isEmpty()) {
            rule = currentDateTime + "_" + tableName + ".backup"
        } else {
            rule = rule.replace("<TABLE>".toRegex(), tableName)
            rule = rule.replace("<ID>".toRegex(), key)
            rule = rule.replace("<DATE>".toRegex(), currentDateTime)
        }

        return rule
    }

    fun isMultipleFile(tableName: String): Boolean {
        val rule = getRule(tableName)
        return rule != null && rule.contains("<ID>")
    }

    @Suppress("UNCHECKED_CAST")
    fun readJson(filename: String): Map<String, Any> {
        val mapper = ObjectMapper()
        return mapper.readValue(File(filename), LinkedHashMap::class.java) as Map<String, Any>
    }

    fun writeJson(filename: String, data: Map<String, Any>) {
        try {
            val path = filename.substring(0, filename.lastIndexOf("/"))
            val folder = File(path)

            if (!folder.exists()) {
                if (!folder.mkdir()) {
                    throw IOException("Cannot make folder $path")
                }
            }

            val mapper = ObjectMapper()
            mapper.writerWithDefaultPrettyPrinter().writeValue(File(filename), data)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
