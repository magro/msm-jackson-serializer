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

import net.spy.memcached.transcoders.SerializingTranscoder;

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardSession;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

/**
 * A {@link net.spy.memcached.transcoders.Transcoder} that serializes catalina
 * {@link StandardSession}s using jackson (json).
 * 
 * @author <a href="mailto:martin.grotzke@javakaffee.de">Martin Grotzke</a>
 */
public class JacksonTranscoder extends SerializingTranscoder {
    
    private final Manager _manager;
    private final ObjectMapper _mapper;

    /**
     * Constructor.
     * 
     * @param manager
     *            the manager
     */
    public JacksonTranscoder( final Manager manager ) {
        _manager = manager;

        _mapper = new ObjectMapper()
                .configure( SerializationConfig.Feature.USE_ANNOTATIONS, false )
                .configure( SerializationConfig.Feature.AUTO_DETECT_FIELDS, false )
                .configure( DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false );
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
            _mapper.writeValue( bos, o );
            return bos.toByteArray();
        } catch ( final Exception e ) {
            throw new IllegalArgumentException( "Non-serializable object", e );
        } finally {
            closeSilently( bos );
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
            final StandardSession session = _mapper.readValue( bis, StandardSession.class );
            session.setManager( _manager );
            return session;
        } catch ( final IOException e ) {
            getLogger().warn( "Caught IOException decoding %d bytes of data", in.length, e );
            throw new RuntimeException( "Caught IOException decoding data", e );
        } finally {
            closeSilently( bis );
        }
    }

}
