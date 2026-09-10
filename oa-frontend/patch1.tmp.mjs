import fs from 'fs';
let s = fs.readFileSync('public/海峡金OA审批系统原型.html', 'utf8');
const swap = (from, to, tag) => {
  if (!s.includes(from)) { console.log('MISS: ' + tag); process.exitCode = 1; return; }
  s = s.replace(from, to);
  console.log('OK: ' + tag);
};

swap(
  '<el-table :data="postDicts"><el-table-column label="归属部门" width="180"><template #default="s">{{deptNameById[s.row.departmentId] || \'通用\'}}</template></el-table-column><el-table-column prop="name" label="岗位名称"></el-table-column>',
  '<el-table :data="postDicts"><el-table-column prop="name" label="岗位名称"></el-table-column>',
  '岗位表格列');

swap(
  '<el-form-item label="归属部门"><el-tree-select v-model="editingPost.departmentId" :data="departmentsTree" node-key="id" check-strictly :props="{label:\'name\',children:\'children\'}" default-expand-all placeholder="选择岗位所属部门" style="width:100%"></el-tree-select></el-form-item><el-form-item label="岗位名称">',
  '<el-form-item label="岗位名称">',
  '岗位弹窗');

swap(
  '<el-option v-for="p in employeePostOptions" :key="p.id" :label="p.name" :value="p.id"></el-option>',
  '<el-option v-for="p in postDicts" :key="p.id" :label="p.name" :value="p.id"></el-option>',
  '员工岗位选项');

fs.writeFileSync('public/海峡金OA审批系统原型.html', s);
