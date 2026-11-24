# Leapy added begin: Inspect compiled API/visibility without loading Android classes.
import struct


def inspect(data):
    offset = 0

    def u1():
        nonlocal offset
        result = data[offset]
        offset += 1
        return result

    def u2():
        nonlocal offset
        result = struct.unpack_from('>H', data, offset)[0]
        offset += 2
        return result

    def u4():
        nonlocal offset
        result = struct.unpack_from('>I', data, offset)[0]
        offset += 4
        return result

    if u4() != 0xCAFEBABE:
        raise ValueError('Invalid class file')
    u2()
    u2()
    pool = [None] * u2()
    class_indices = []
    string_indices = []
    index = 1
    while index < len(pool):
        tag = u1()
        if tag == 1:
            length = u2()
            pool[index] = data[offset:offset + length].decode('utf-8', errors='replace')
            offset += length
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = u2()
            if tag == 7:
                class_indices.append(index)
            elif tag == 8:
                string_indices.append(index)
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        elif tag == 15:
            offset += 3
        else:
            raise ValueError(f'Unsupported constant-pool tag {tag}')
        index += 1

    access = u2()
    this_class = u2()
    name = pool[pool[this_class]]
    super_index = u2()
    parent = pool[pool[super_index]] if super_index else None
    interfaces = [pool[pool[u2()]] for _ in range(u2())]

    def attributes():
        nonlocal offset
        result = {}
        for _ in range(u2()):
            key = pool[u2()]
            size = u4()
            result[key] = data[offset:offset + size]
            offset += size
        return result

    def members():
        result = []
        for _ in range(u2()):
            flags, member, descriptor = u2(), pool[u2()], pool[u2()]
            attributes()
            result.append((flags, member, descriptor))
        return result

    fields, methods = members(), members()
    attrs = attributes()
    nested = attrs.get('InnerClasses', b'\0\0')
    anonymous = False
    for index in range(struct.unpack_from('>H', nested)[0]):
        inner, outer, simple, flags = struct.unpack_from('>HHHH', nested, 2 + index * 8)
        if inner == this_class:
            access = flags
            anonymous = simple == 0
    return dict(name=name, access=access, anonymous=anonymous, parent=parent,
                interfaces=interfaces, fields=fields, methods=methods,
                references=[pool[pool[i]] for i in class_indices],
                literals=[pool[pool[i]] for i in string_indices],
                strings=[value for value in pool if isinstance(value, str)],
                signature=pool[struct.unpack('>H',attrs['Signature'])[0]] if 'Signature' in attrs else '')
# Leapy end
