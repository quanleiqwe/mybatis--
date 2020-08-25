/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.ibatis.cache.Cache;

/**
 * Lru (least recently used) cache decorator.
 * 最近最少使用 策略
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  private final Cache delegate;
  // 维护LRU key的一个map , 这里最后实例话的子类是LinkedHashMap,key 为缓存的key , value 也是缓存的 key,
  // 这里解释一下，当初始化的时候accessOrder 为true,表示根据访问来进行排序，为false则根据插入的顺序进行排序，当添加一个key 的时候，这里我们传的是true，
  // LinkedHashMap 会将对应的entry移动到末尾，移除一个key时，会根据这个方法removeEldestEntry 来判断是否需要移除最老的entry ，
  private Map<Object, Object> keyMap;
  // 当前最老得一个key
  private Object eldestKey;

  public LruCache(Cache delegate) {
    this.delegate = delegate;
    setSize(1024);
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(final int size) {
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;
      //该方法会在插入的时候进行调用,来判断是否需要移除最老的entry
      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        boolean tooBig = size() > size;
        if (tooBig) {
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  @Override
  public void putObject(Object key, Object value) {
    delegate.putObject(key, value);
    //清除最老的key
    cycleKeyList(key);
  }

  @Override
  public Object getObject(Object key) {
    //将该key 移动到末尾
    keyMap.get(key); // touch
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  private void cycleKeyList(Object key) {
    keyMap.put(key, key);
    if (eldestKey != null) {
      delegate.removeObject(eldestKey);
      eldestKey = null;
    }
  }

}
