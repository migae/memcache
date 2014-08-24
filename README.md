## API

```
void clearAll()		mc/Cache reinit
boolean contains?(keyO  (contains? mc/Cache theKey)

boolean delete(...)	(dissoc mc/Cache theKey)
keys deleteAll(keys)	(dissoc mc/Cache theKey & theKeys)

obj 	get(theKey)	(mc/Cache theKey)
map<T, obj> getAll(keys)(mc/Cache theKeys)

IdentifiableValue getIdentifiable(key)

stats getStatistics

Long increment(key, delta)
map<T, long) incrementAll(keys, delta)

    add:  decrement

void put(key, val)   (assoc mc/Cache key val), (into mc/Cache {key val}), etc
void putAll

set<T> putIfUntouched
```
