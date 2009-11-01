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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardSession;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;

import de.javakaffee.web.msm.MemcachedBackupSessionManager;
import de.javakaffee.web.msm.serializer.jackson.JacksonTranscoderTest.Person.Gender;

/**
 * @author Martin Grotzke (martin.grotzke@freiheit.com) (initial creation)
 */
public class JacksonTranscoderTest {

    @Test
    public void testReadValueIntoObject() throws Exception {
        final MemcachedBackupSessionManager manager = new MemcachedBackupSessionManager();
        manager.setContainer( new StandardContext() );
        final JacksonTranscoder transcoder = new JacksonTranscoder( manager );

        final StandardSession session = manager.createEmptySession();
        session.setValid( true );

        session.setId( "foo" );

        session.setAttribute( "person1", createPerson( "foo bar", Gender.MALE, "foo.bar@example.org", "foo.bar@example.com" ) );
        session.setAttribute( "person2", createPerson( "bar baz", Gender.FEMALE, "bar.baz@example.org", "bar.baz@example.com" ) );

        final byte[] json = transcoder.serialize( session );
        System.out.println( "Have json: " + new String(json) );
        final StandardSession readJSONValue = (StandardSession) transcoder.deserialize( json );
        assertEquals( readJSONValue, session );

        final StandardSession readJavaValue = javaRoundtrip( session, manager );
        assertEquals( readJavaValue, session );

        assertEquals( readJSONValue, readJavaValue );

        System.out.println( ToStringBuilder.reflectionToString( session ) );
        System.out.println( ToStringBuilder.reflectionToString( readJSONValue ) );
        System.out.println( ToStringBuilder.reflectionToString( readJavaValue ) );

    }

    private Person createPerson( final String name, final Gender gender, final String... emailAddresses ) {
        final Person person = new Person();
        person.setName( name );
        person.setGender( gender );
        final HashMap<String, Object> props = new HashMap<String, Object>();
        for ( int i = 0; i < emailAddresses.length; i++ ) {
            final String emailAddress = emailAddresses[i];
            props.put( "email" + i, new Email( name, emailAddress ) );
        }
        person.setProps( props );
        return person;
    }

    private Field getField( final Class<?> clazz, final String name ) throws NoSuchFieldException {
        final Field field = clazz.getDeclaredField( name );
        field.setAccessible( true );
        return field;
    }
    
    /*
     * person2=Person
     * [_gender=FEMALE, _name=bar baz,
     *      _props={email0=Email [_email=bar.baz@example.org, _name=bar baz],
     *          email1=Email [_email=bar.baz@example.com, _name=bar baz]}],
     * person1=Person [_gender=MALE, _name=foo bar,
     *      _props={email0=Email [_email=foo.bar@example.org, _name=foo bar],
     *          email1=Email [_email=foo.bar@example.com, _name=foo bar]}]}
     *          
     * but was:
     * person2={name=bar baz,
     *      props={email0={name=bar baz, email=bar.baz@example.org},
     *          email1={name=bar baz, email=bar.baz@example.com}}, gender=FEMALE}
     * person1={name=foo bar,
     *      props={email0={name=foo bar, email=foo.bar@example.org},
     *          email1={name=foo bar, email=foo.bar@example.com}}, gender=MALE}}
     */

    private void assertEquals( final Object one, final Object another ) throws Exception {
        if ( one == another ) {
            return;
        }
        if ( one == null && another != null || one != null && another == null ) {
            Assert.fail( "One of both is null: " + one + ", " + another );
        }
        Assert.assertEquals( one.getClass(), another.getClass() );
        if ( one.getClass().isPrimitive() || one instanceof String || Number.class.isAssignableFrom( one.getClass() )
                || one instanceof Boolean || one instanceof Map<?,?> ) {
            Assert.assertEquals( one, another );
            return;
        }

        Class<? extends Object> clazz = one.getClass();
        while ( clazz != null ) {
            assertEqualDeclaredFields( clazz, one, another );
            clazz = clazz.getSuperclass();
        }

    }

    private void assertEqualDeclaredFields( final Class<? extends Object> clazz, final Object one, final Object another )
        throws Exception, IllegalAccessException {
        for ( final Field field : clazz.getDeclaredFields() ) {
            field.setAccessible( true );
            assertEquals( field.get( one ), field.get( another ) );
        }
    }

    private StandardSession javaRoundtrip( final StandardSession session, final MemcachedBackupSessionManager manager )
        throws IOException, ClassNotFoundException {

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream( bos );
        session.writeObjectData( oos );
        oos.close();
        bos.close();

        final ByteArrayInputStream bis = new ByteArrayInputStream( bos.toByteArray() );
        final ObjectInputStream ois = new ObjectInputStream( bis );
        final StandardSession readSession = manager.createEmptySession();
        readSession.readObjectData( ois );
        ois.close();
        bis.close();

        return readSession;
    }

    static class Person implements Serializable {

        private static final long serialVersionUID = 1L;

        static enum Gender {
                MALE,
                FEMALE
        }

        private String _name;
        private Gender _gender;
        private Map<String, Object> _props;

        public String getName() {
            return _name;
        }

        public void setName( final String name ) {
            _name = name;
        }

        public Map<String, Object> getProps() {
            return _props;
        }

        public void setProps( final Map<String, Object> props ) {
            _props = props;
        }

        public Gender getGender() {
            return _gender;
        }

        public void setGender( final Gender gender ) {
            _gender = gender;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ( ( _gender == null )
                ? 0
                : _gender.hashCode() );
            result = prime * result + ( ( _name == null )
                ? 0
                : _name.hashCode() );
            result = prime * result + ( ( _props == null )
                ? 0
                : _props.hashCode() );
            return result;
        }

        @Override
        public boolean equals( final Object obj ) {
            if ( this == obj )
                return true;
            if ( obj == null )
                return false;
            if ( getClass() != obj.getClass() )
                return false;
            final Person other = (Person) obj;
            if ( _gender == null ) {
                if ( other._gender != null )
                    return false;
            } else if ( !_gender.equals( other._gender ) )
                return false;
            if ( _name == null ) {
                if ( other._name != null )
                    return false;
            } else if ( !_name.equals( other._name ) )
                return false;
            if ( _props == null ) {
                if ( other._props != null )
                    return false;
            } else if ( !_props.equals( other._props ) )
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "Person [_gender=" + _gender + ", _name=" + _name + ", _props=" + _props + "]";
        }

    }

    static class Email implements Serializable {

        private static final long serialVersionUID = 1L;

        private String _name;
        private String _email;

        public Email( final String name, final String email ) {
            super();
            _name = name;
            _email = email;
        }

        public String getName() {
            return _name;
        }

        public void setName( final String name ) {
            _name = name;
        }

        public String getEmail() {
            return _email;
        }

        public void setEmail( final String email ) {
            _email = email;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ( ( _email == null )
                ? 0
                : _email.hashCode() );
            result = prime * result + ( ( _name == null )
                ? 0
                : _name.hashCode() );
            return result;
        }

        @Override
        public boolean equals( final Object obj ) {
            if ( this == obj )
                return true;
            if ( obj == null )
                return false;
            if ( getClass() != obj.getClass() )
                return false;
            final Email other = (Email) obj;
            if ( _email == null ) {
                if ( other._email != null )
                    return false;
            } else if ( !_email.equals( other._email ) )
                return false;
            if ( _name == null ) {
                if ( other._name != null )
                    return false;
            } else if ( !_name.equals( other._name ) )
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "Email [_email=" + _email + ", _name=" + _name + "]";
        }

    }

}
