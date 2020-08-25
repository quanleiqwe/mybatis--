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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/** 事务的缓存,进行commit 和rollback 都会清除缓存,二级缓存
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);
  /**
   * 底层封装的二级缓存对应的Cache 对象
   */
  private final Cache delegate;
  /**
   * 为true 是，表示当前TransactionalCache 不可查，且提交事务时会将底层cache 清空
   */
  private boolean clearOnCommit;
  /**
   * 暂时记录添加到 TransactionalCache中的任务，当事务提交时，会将其中的数据添加到二级缓存中
   */
  private final Map<Object, Object> entriesToAddOnCommit;
  /**
   * 记录缓存未命中的CacheKey 对象
   */
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    // issue #116
    // 先从底层cache 获取
    Object object = delegate.getObject(key);
    // 没有的话，添加到 entriesMissedInCache 集合
    if (object == null) {
      entriesMissedInCache.add(key);
    }
    // issue #146
    // 如果为true, 当前cache 不可查，始终返回null
    if (clearOnCommit) {
      return null;
    } else {
      return object;
    }
  }

  /**
   * 这里并没有放入真正的缓存，只是暂存，只有执行commit 方法后，才会真正放入
   * @param key
   *          Can be any object but usually it is a {@link CacheKey}
   * @param object
   */
  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  /**
   * commit ，提交事务，清空二级缓存
   */
  public void commit() {
    // 清除缓存
    if (clearOnCommit) {
      delegate.clear();
    }
    // 将entriesToAddOnCommit 中的数据保存到二级缓存中
    flushPendingEntries();
    reset();
  }

  /**
   * 回滚操作
   */
  public void rollback() {
    unlockMissedEntries();
    reset();
  }

  // 恢复到初始状态
  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 将entriesToAddOnCommit 中的数据保存到二级缓存中
   */
  private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifiying a rollback to the cache adapter. "
            + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}
