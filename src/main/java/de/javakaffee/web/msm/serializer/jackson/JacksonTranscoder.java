/*
 * Copyright 2009 Martin Grotzke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an &quot;AS IS&quot; BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package de.javakaffee.web.msm.serializer.jackson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.spy.memcached.transcoders.SerializingTranscoder;

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardSession;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.codehaus.jackson.type.TypeReference;

/**
 * A {@link net.spy.memcached.transcoders.Transcoder} that serializes catalina
 * {@link StandardSession}s using jackson (json).
 * 
 * @author <a href="mailto:martin.grotzke@javakaffee.de">Martin Grotzke</a>
 */
public class JacksonTranscoder extends SerializingTranscoder {

    private final Manager _manager;
    private final JsonFactory _jsonFactory;
    private final ConcurrentHashMap<String, Field> _fieldMap;

    /**
     * Constructor.
     * 
     * @param manager
     *            the manager
     */
    public JacksonTranscoder( final Manager manager ) {
        _manager = manager;
        _jsonFactory = new MappingJsonFactory();
        _fieldMap = new ConcurrentHashMap<String, Field>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected byte[] serialize( final Object o ) {
        if ( o == null ) {
            throw new NullPointerException( "Can't serialize null" );
        }

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {

            final JsonGenerator generator = _jsonFactory.createJsonGenerator( bos, JsonEncoding.UTF8 );
            generator.writeStartObject();
            writeFields( generator, o );
            generator.writeEndObject();
            generator.close();

            return bos.toByteArray();
            
        } catch ( final Exception e ) {
            throw new IllegalArgumentException( "Non-serializable object", e );
        } finally {
            closeSilently( bos );
        }

    }

    private void writeFields( final JsonGenerator generator, final Object o ) throws IOException, JsonGenerationException,
        IllegalAccessException, NoSuchFieldException, JsonProcessingException {
        generator.writeNumberField( "lastAccessedTime", getField( "lastAccessedTime" ).getLong( o ) );
        generator.writeNumberField( "maxInactiveInterval", getField( "maxInactiveInterval" ).getInt( o ) );
        generator.writeBooleanField( "isNew", getField( "isNew" ).getBoolean( o ) );
        generator.writeBooleanField( "isValid", getField( "isValid" ).getBoolean( o ) );
        generator.writeNumberField( "thisAccessedTime", getField( "thisAccessedTime" ).getLong( o ) );
        generator.writeNumberField( "creationTime", getField( "creationTime" ).getLong( o ) );
        generator.writeStringField( "id", (String) getField( "id" ).get( o ) );
        generator.writeObjectField( "attributes", getField( "attributes" ).get( o ) );
    }

    private Field getField( final String name ) throws NoSuchFieldException {
        Field field = _fieldMap.get( name );
        if ( field == null ) {
            field = StandardSession.class.getDeclaredField( name );
            field.setAccessible( true );
            _fieldMap.putIfAbsent( name, field );
        }
        return field;
    }

    /**
     * Get the object represented by the given serialized bytes.
     * 
     * @param in
     *            the bytes to deserialize
     * @return the resulting object
     */
    @Override
    protected Object deserialize( final byte[] in ) {
        final ByteArrayInputStream bis = new ByteArrayInputStream( in );
        try {
            final StandardSession session = (StandardSession) _manager.createEmptySession();
            
            final JsonParser jp = _jsonFactory.createJsonParser(bis);
            final JsonToken token = jp.nextToken();
            if ( token != JsonToken.START_OBJECT ) {
                throw new IllegalStateException("START_OBJECT expected, got " + token );
            }
            while (jp.nextToken() != JsonToken.END_OBJECT) {
                final String fieldname = jp.getCurrentName();
                jp.nextToken();
                readField( jp, fieldname, session );
            }
            
            return session;
        } catch ( final Exception e ) {
            getLogger().warn( "Caught IOException decoding %d bytes of data", in.length, e );
            throw new RuntimeException( "Caught IOException decoding data", e );
        } finally {
            closeSilently( bis );
        }
    }

    private void readField( final JsonParser jp, final String fieldname, final StandardSession session ) throws IOException,
        JsonProcessingException, IllegalAccessException, NoSuchFieldException, JsonParseException {
        if ("attributes".equals(fieldname)) {
            final Map<String, Object> values = jp.readValueAs( new TypeReference<Map<String,Object>>() {} );
            final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<String, Object>( values );
            getField( "attributes" ).set( session, attributes );
        }
        else if ( fieldname.equals( "creationTime" )
                || fieldname.equals( "lastAccessedTime" )
                || fieldname.equals( "thisAccessedTime" ) ) {
            getField( fieldname ).setLong( session, jp.getLongValue() );
        }
        else if ( fieldname.equals( "maxInactiveInterval" )) {
            getField( fieldname ).setInt( session, jp.getIntValue() );
        }
        else if ( fieldname.equals( "isNew" ) || fieldname.equals( "isValid" ) ) {
            getField( fieldname ).setBoolean( session, jp.getCurrentToken() == JsonToken.VALUE_TRUE );
        }
        else if ( fieldname.equals( "id" ) ) {
            getField( fieldname ).set( session, jp.getText() );
        }
        else {
            throw new IllegalStateException("Unrecognized field '"+fieldname+"'!");
        }
    }

    private void closeSilently( final OutputStream os ) {
        if ( os != null ) {
            try {
                os.close();
            } catch ( final IOException f ) {
                // fail silently
            }
        }
    }

    private void closeSilently( final InputStream is ) {
        if ( is != null ) {
            try {
                is.close();
            } catch ( final IOException f ) {
                // fail silently
            }
        }
    }

}
