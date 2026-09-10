import fs from 'fs';
let s = fs.readFileSync('public/海峡金OA审批系统原型.html', 'utf8');
const swap = (f, t, tag) => {
  if (!s.includes(f)) { console.log('MISS: ' + tag); process.exitCode = 1; return; }
  s = s.replace(f, t);
  console.log('OK: ' + tag);
};
swap('<el-form-item label="分配角色"><el-select v-model="newPerson.position"><el-option v-for="r in roleConfigs" :label="r.name" :value="r.name"></el-option></el-select></el-form-item></div><el-alert title="员工权限由所分配角色自动继承',
  '<el-form-item label="分配角色"><el-select v-model="newPerson.position"><el-option v-for="r in roleConfigs" :label="r.name" :value="r.name"></el-option></el-select></el-form-item><el-form-item label="直属主管（汇报线）"><el-select v-model="newPerson.managerAccount" filterable clearable placeholder="选择直属主管，可留空" style="width:100%"><el-option v-for="m in managerOptions" :key="m.account" :label="m.name" :value="m.account"></el-option></el-select></el-form-item></div><el-alert title="员工权限由所分配角色自动继承', '员工直属主管选择');
swap('<el-table-column prop="post" label="岗位" width="130"></el-table-column>',
  '<el-table-column prop="post" label="岗位" width="130"></el-table-column><el-table-column prop="managerName" label="直属主管" width="110"></el-table-column>', '人员表格列');
fs.writeFileSync('public/海峡金OA审批系统原型.html', s);
