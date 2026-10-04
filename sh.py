# -*- coding: utf-8 -*-
import io, re
p = 'gradle/mod_text.properties'
s = io.open(p, encoding='ascii').read()
# 中文描述改成两句的简短版
new_cn = '自动孖变助手：一直孖变直到出现标记物品。'
new_cn = '自动孖变助手：一直孖变直到出现标记物品。'
new_cn = '自动嬖变助手：一直嬖变直到出现标记物品。'
new_cn = '自动嬖变助手：一直嬖变直到出现标记物品。'
new_cn = '自动嬖变助手：一直嬖变直到出现标记物品。'
# 正确写法
new_cn = '自动嬖变助手：一直嬖变直到出现标记物品。'
new_cn = '自动嬖变助手'.replace('嬖','嬗') + '：一直' + '嬗' + '变直到出现标记物品。'
def esc(s):
    return ''.join(c if ord(c) < 128 else '\\u%04X' % ord(c) for c in s)
s = re.sub(r'^mod_summary_cn=.*$', 'mod_summary_cn=' + esc(new_cn), s, flags=re.M)
io.open(p, 'w', encoding='ascii', newline='\n').write(s)
# 还原验证
t = io.open(p, encoding='ascii').read()
for line in t.split('\n'):
    if line.startswith('mod_summary_cn='):
        raw = line.split('=',1)[1]
        print('还原后:', re.sub(r'\\u([0-9A-Fa-f]{4})', lambda g: chr(int(g.group(1),16)), raw))
